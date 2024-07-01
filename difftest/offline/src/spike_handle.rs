use std::collections::VecDeque;
use std::fs::File;
use std::io::Read;
use std::path::Path;
use tracing::info;
use xmas_elf::program::{ProgramHeader, Type};
use xmas_elf::{header, ElfFile};

use libspike_rs::c_interface::SpikeCHandler;
use libspike_rs::clip;
use libspike_rs::spike_event::SpikeEvent;

use crate::dut::{IssueEvent, LsuEnqEvent, VrfWriteEvent};

const LSU_IDX_DEFAULT: u8 = 0xff;

fn load_elf(spike: &mut SpikeCHandler, fname: &Path) -> anyhow::Result<u64> {
  let mut file = File::open(fname).unwrap();
  let mut buffer = Vec::new();
  file.read_to_end(&mut buffer).unwrap();

  let elf_file = ElfFile::new(&buffer).unwrap();

  let header = elf_file.header;
  assert_eq!(header.pt2.machine().as_machine(), header::Machine::RISC_V);
  assert_eq!(header.pt1.class(), header::Class::ThirtyTwo);

  for ph in elf_file.program_iter() {
    if let ProgramHeader::Ph32(ph) = ph {
      if ph.get_type() == Ok(Type::Load) {
        let offset = ph.offset as usize;
        let size = ph.file_size as usize;
        let addr = ph.virtual_addr as usize;

        let slice = &buffer[offset..offset + size];
        spike.load_bytes_to_mem(addr, size, slice.to_vec()).unwrap();
      }
    }
  }

  Ok(header.pt2.entry_point())
}

pub struct Config {
  pub vlen: u32,
  pub dlen: u32,
}

pub fn add_rtl_write(se: &mut SpikeEvent, vrf_write: VrfWriteEvent, record_idx_base: usize) {
  (0..4).for_each(|j| {
    if ((vrf_write.mask >> j) & 1) != 0 {
      let written_byte = ((vrf_write.data >> (8 * j)) & 0xff) as u8;
      let record_iter = se
        .vrf_access_record
        .all_writes
        .get_mut(&(record_idx_base + j));

      if let Some(record) = record_iter {
        assert_eq!(
          record.byte,
          written_byte,
          "{j}th byte incorrect ({:02X} != {written_byte:02X}) for vrf \
            write (lane={}, vd={}, offset={}, mask={:04b}) \
            [vrf_idx={}] (lsu_idx={}, disasm: {}, pc: {:#x}, bits: {:#x})",
          record.byte,
          vrf_write.idx,
          vrf_write.vd,
          vrf_write.offset,
          vrf_write.mask,
          record_idx_base + j,
          se.lsu_idx,
          se.disasm,
          se.pc,
          se.inst_bits
        );
        record.executed = true;
      }
    } // end if mask
  }) // end for j
}

pub struct SpikeRunner {
  spike: Box<SpikeCHandler>,

  /// to rtl stack
  /// in the spike thread, spike should detech if this queue is full, if not
  /// full, execute until a vector instruction, record the behavior of this
  /// instruction, and send to str_stack. in the RTL thread, the RTL driver will
  /// consume from this queue, drive signal based on the queue. size of this
  /// queue should be as big as enough to make rtl free to run, reducing the
  /// context switch overhead.
  pub to_rtl_queue: VecDeque<SpikeEvent>,

  /// config for v extension
  pub config: Config,

  /// implement the get_t() for mcycle csr update
  pub cycle: usize,

  /// for mcycle csr update
  pub spike_cycle: usize,
}

impl SpikeRunner {
  pub fn new(size: usize, fname: &Path, vlen: u32, dlen: u32, set: String) -> Self {
    // load the elf file
    // initialize spike
    let arch = &format!("vlen:{vlen},elen:32");
    let lvl = "M";

    let mut spike = SpikeCHandler::new(arch, &set, lvl, (dlen / 32) as usize, size);

    let entry_addr = load_elf(&mut spike, fname).unwrap();

    // initialize processor
    let proc = spike.get_proc();
    let state = proc.get_state();
    proc.reset();
    state.set_pc(entry_addr);

    SpikeRunner {
      spike,
      to_rtl_queue: VecDeque::new(),
      config: Config { vlen, dlen },
      cycle: 0,
      spike_cycle: 0,
    }
  }

  // just execute one instruction for no-difftest
  pub fn exec(&self) -> anyhow::Result<()> {
    let spike = &self.spike;
    let proc = spike.get_proc();
    let state = proc.get_state();

    let new_pc = proc.func();

    state.handle_pc(new_pc).unwrap();

    let ret = state.exit();

    if ret == 0 {
      return Err(anyhow::anyhow!("simulation finished!"));
    }

    Ok(())
  }

  // execute the spike processor for one instruction and record
  // the spike event for difftest
  pub fn spike_step(&mut self) -> Option<SpikeEvent> {
    let proc = self.spike.get_proc();
    let state = proc.get_state();

    state.set_mcycle(self.cycle + self.spike_cycle);

    let pc = state.get_pc();
    let disasm = proc.disassemble();

    let mut event = self.create_spike_event();
    state.clear();

    let new_pc;
    match event {
      // inst is load / store / v / quit
      Some(ref mut se) => {
        info!(
          "[{}] SpikeStep: spike run vector insn, pc={:#x}, disasm={:?}, spike_cycle={:?}",
          self.cycle, pc, disasm, self.spike_cycle
        );
        se.pre_log_arch_changes(&self.spike, self.config.vlen)
          .unwrap();
        new_pc = proc.func();
        se.log_arch_changes(&self.spike, self.config.vlen).unwrap();
      }
      None => {
        info!(
          "[{}] SpikeStep: spike run scalar insn, pc={:#x}, disasm={:?}, spike_cycle={:?}",
          self.cycle, pc, disasm, self.spike_cycle
        );
        new_pc = proc.func();
      }
    }

    state.handle_pc(new_pc).unwrap();

    self.spike_cycle += 1;

    event
  }

  // step the spike processor until the instruction is load/store/v/quit
  // if the instruction is load/store/v/quit, execute it and return
  fn create_spike_event(&mut self) -> Option<SpikeEvent> {
    let spike = &self.spike;
    let proc = spike.get_proc();

    let insn = proc.get_insn();

    let opcode = clip(insn, 0, 6);
    let width = clip(insn, 12, 14);
    let rs1 = clip(insn, 15, 19);
    let csr = clip(insn, 20, 31);

    // early return vsetvl scalar instruction
    let is_vsetvl = opcode == 0b1010111 && width == 0b111;
    if is_vsetvl {
      return None;
    }

    let is_load_type = opcode == 0b0000111 && (((width - 1) & 0b100) != 0);
    let is_store_type = opcode == 0b0100111 && (((width - 1) & 0b100) != 0);
    let is_v_type = opcode == 0b1010111;

    let is_csr_type = opcode == 0b1110011 && ((width & 0b011) != 0);
    let is_csr_write = is_csr_type && (((width & 0b100) | rs1) != 0);

    let is_quit = is_csr_write && csr == 0x7cc;

    if is_load_type || is_store_type || is_v_type || is_quit {
      return SpikeEvent::new(spike);
    }
    None
  }

  pub fn find_se_to_issue(&mut self) -> SpikeEvent {
    // find the first instruction that is not issued from the back
    for se in self.to_rtl_queue.iter().rev() {
      if !se.is_issued {
        return se.clone();
      }
    }

    loop {
      if let Some(se) = self.spike_step() {
        self.to_rtl_queue.push_front(se.clone());
        return se;
      }
    }
  }

  pub fn peek_issue(&mut self, issue: IssueEvent) -> anyhow::Result<()> {
    let se = self.to_rtl_queue.front_mut().unwrap();
    if se.is_vfence_insn || se.is_exit_insn {
      return Ok(());
    }

    se.is_issued = true;
    se.issue_idx = issue.idx as u8;

    info!(
      "[{}] SpikePeekIssue: idx={}, pc={:#x}, inst={}",
      issue.cycle, issue.idx, se.pc, se.disasm
    );

    Ok(())
  }

  pub fn update_lsu_idx(&mut self, lsu_enq: LsuEnqEvent) -> anyhow::Result<()> {
    let enq = lsu_enq.enq;
    assert!(enq > 0, "enq should be greater than 0");
    let cycle = lsu_enq.cycle;

    if let Some(se) = self
      .to_rtl_queue
      .iter_mut()
      .rev()
      .find(|se| se.is_issued && (se.is_load || se.is_store) && se.lsu_idx == LSU_IDX_DEFAULT)
    {
      let index = enq.trailing_zeros() as u8;
      se.lsu_idx = index;
      info!("[{cycle}] UpdateLSUIdx: Instruction is allocated with pc: {:#x}, inst: {} and lsu_idx: {index}", se.pc, se.disasm);
    }
    Ok(())
  }

  pub fn peek_vrf_write_from_lsu(&mut self, vrf_write: VrfWriteEvent) -> anyhow::Result<()> {
    let cycle = vrf_write.cycle;
    let vlen_in_bytes = self.config.vlen / 8;
    let lane_number = self.config.dlen / 32;
    let record_idx_base = (vrf_write.vd * vlen_in_bytes
      + (vrf_write.idx + lane_number * vrf_write.offset) * 4) as usize;

    if let Some(se) = self
      .to_rtl_queue
      .iter_mut()
      .rev()
      .find(|se| se.issue_idx == vrf_write.instruction as u8)
    {
      info!("[{cycle}] RecordRFAccesses: lane={}, vd={}, offset={}, mask={:04b}, data={:08x}, instruction={}, rtl detect vrf queue write" , vrf_write.idx, vrf_write.vd, vrf_write.offset, vrf_write.mask, vrf_write.data, vrf_write.instruction);

      add_rtl_write(se, vrf_write, record_idx_base);
      return Ok(());
    }

    panic!(
      "[{cycle}] cannot find se with issue_idx={}",
      vrf_write.instruction
    )
  }

  pub fn peek_vrf_write_from_lane(&mut self, vrf_write: VrfWriteEvent) -> anyhow::Result<()> {
    let cycle = vrf_write.cycle;
    let vlen_in_bytes = self.config.vlen / 8;
    let lane_number = self.config.dlen / 32;
    let record_idx_base = (vrf_write.vd * vlen_in_bytes
      + (vrf_write.idx + lane_number * vrf_write.offset) * 4) as usize;

    if let Some(se) = self
      .to_rtl_queue
      .iter_mut()
      .rev()
      .find(|se| se.issue_idx == vrf_write.instruction as u8)
    {
      if !se.is_load {
        info!("[{cycle}] RecordRFAccesses: lane={}, vd={}, offset={}, mask={:04b}, data={:08x}, instruction={}, rtl detect vrf write", vrf_write.idx, vrf_write.vd, vrf_write.offset, vrf_write.mask, vrf_write.data, vrf_write.instruction);

        add_rtl_write(se, vrf_write, record_idx_base);
      }
      return Ok(());
    }

    info!("[{cycle}] RecordRFAccess: index={} rtl detect vrf write which cannot find se, maybe from committed load insn", vrf_write.idx);
    Ok(())
  }
}
