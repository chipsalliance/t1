use lazy_static::lazy_static;
use std::collections::VecDeque;
use std::fs::File;
use std::io::Read;
use std::path::Path;
use std::sync::Mutex;
use tracing::{info, trace, warn};
use xmas_elf::{
  header,
  program::{ProgramHeader, Type},
  ElfFile,
};

mod libspike_interfaces;
use libspike_interfaces::*;

mod spike_event;
use spike_event::*;

use super::dut::*;

const MSHR: usize = 3;
const LSU_IDX_DEFAULT: u8 = 0xff;

// read the addr from spike memory
// caller should make sure the address is valid
#[no_mangle]
pub extern "C" fn rs_addr_to_mem(addr: u64) -> *mut u8 {
  let addr = addr as usize;
  let mut spike_mem = SPIKE_MEM.lock().unwrap();
  let spike_mut = spike_mem.as_mut().unwrap();
  &mut spike_mut.mem[addr] as *mut u8
}

pub struct SpikeMem {
  pub mem: Vec<u8>,
  pub size: usize,
}

lazy_static! {
  static ref SPIKE_MEM: Mutex<Option<Box<SpikeMem>>> = Mutex::new(None);
}

fn init_memory(size: usize) {
  let mut spike_mem = SPIKE_MEM.lock().unwrap();
  if spike_mem.is_none() {
    info!("Creating SpikeMem with size: 0x{:x}", size);
    *spike_mem = Some(Box::new(SpikeMem {
      mem: vec![0; size],
      size,
    }));
  }
}

fn ld(addr: usize, len: usize, bytes: Vec<u8>) -> anyhow::Result<()> {
  trace!("ld: addr: 0x{:x}, len: 0x{:x}", addr, len);
  let mut spike_mem = SPIKE_MEM.lock().unwrap();
  let spike_ref = spike_mem.as_mut().unwrap();

  assert!(addr + len <= spike_ref.size);

  let dst = &mut spike_ref.mem[addr..addr + len];
  for (i, byte) in bytes.iter().enumerate() {
    dst[i] = *byte;
  }

  Ok(())
}

fn read_mem(addr: usize) -> anyhow::Result<u8> {
  let mut spike_mem = SPIKE_MEM.lock().unwrap();
  let spike_ref = spike_mem.as_mut().unwrap();

  let dst = &mut spike_ref.mem[addr];

  Ok(*dst)
}

fn load_elf(fname: &Path) -> anyhow::Result<u64> {
  let mut file = File::open(fname).unwrap();
  let mut buffer = Vec::new();
  file.read_to_end(&mut buffer).unwrap();

  let elf_file = ElfFile::new(&buffer).unwrap();

  let header = elf_file.header;
  assert_eq!(header.pt2.machine().as_machine(), header::Machine::RISC_V);
  assert_eq!(header.pt1.class(), header::Class::ThirtyTwo);

  for ph in elf_file.program_iter() {
    match ph {
      ProgramHeader::Ph32(ph) => {
        if ph.get_type() == Ok(Type::Load) {
          let offset = ph.offset as usize;
          let size = ph.file_size as usize;
          let addr = ph.virtual_addr as usize;

          let slice = &buffer[offset..offset + size];
          ld(addr, size, slice.to_vec()).unwrap();
        }
      }
      _ => (),
    }
  }

  Ok(header.pt2.entry_point())
}

pub fn clip(binary: u64, a: i32, b: i32) -> u32 {
  assert!(a <= b, "a should be less than or equal to b");
  let nbits = b - a + 1;
  let mask = if nbits >= 32 {
    u32::MAX
  } else {
    (1 << nbits) - 1
  };
  (binary as u32 >> a) & mask
}

pub struct Config {
  pub vlen: u32,
  pub dlen: u32,
}

pub fn add_rtl_write(
  se: &mut SpikeEvent,
  mask: u32,
  data: u32,
  record_idx_base: usize,
  lane_idx: usize,
  vd: usize,
  offset: usize,
) {
  (0..4).for_each(|j| {
    if ((mask >> j) & 1) != 0 {
      let written_byte = ((data >> (8 * j)) & 0xff) as u8;
      let record_iter = se
        .vrf_access_record
        .all_writes
        .get_mut(&(record_idx_base + j));

      if let Some(record) = record_iter {
        assert_eq!(
          (record.byte as u8),
          (written_byte as u8),
          "{j}th byte incorrect ({:02X} != {written_byte:02X}) for vrf \
						write (lane={lane_idx}, vd={vd}, offset={offset}, mask={mask:04b}) \
						[vrf_idx={}] (lsu_idx={}, {})",
          record.byte,
          record_idx_base + j,
          se.lsu_idx,
          format!(
            "disasm: {}, pc: {:x}, bits: {:#x}",
            se.disasm, se.pc, se.inst_bits
          )
        );
        record.executed = true;
      }
    } // end if mask
  }) // end for j
}
pub struct SpikeHandle {
  spike: Spike,

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
}

impl SpikeHandle {
  pub fn new(size: usize, fname: &Path, vlen: u32, dlen: u32) -> Self {
    // register the addr_to_mem callback
    unsafe { spike_register_callback(rs_addr_to_mem) }

    // create a new spike memory instance
    init_memory(size);

    // load the elf file
    let entry_addr = load_elf(fname).unwrap();

    // initialize spike
    let arch = "vlen:1024,elen:32";
    let set = "rv32gcv";
    let lvl = "M";

    let spike = Spike::new(arch, set, lvl);

    // initialize processor
    let proc = spike.get_proc();
    let state = proc.get_state();
    proc.reset();
    state.set_pc(entry_addr);

    SpikeHandle {
      spike,
      to_rtl_queue: VecDeque::new(),
      config: Config { vlen, dlen },
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

    let pc = state.get_pc();
    let disasm = proc.disassemble();

    let mut event = self.create_spike_event();
    state.clear();

    let new_pc;
    match event {
      // inst is load / store / v / quit
      Some(ref mut se) => {
        info!(
          "SpikeStep: pc={:#x}, disasm={:?}, spike run vector insn",
          pc, disasm
        );
        se.pre_log_arch_changes(&self.spike, self.config.vlen)
          .unwrap();
        new_pc = proc.func();
        se.log_arch_changes(&self.spike, self.config.vlen).unwrap();
      }
      None => {
        info!(
          "SpikeStep: pc={:#x}, disasm={:?}, spike run scalar insn",
          pc, disasm
        );
        new_pc = proc.func();
      }
    }

    state.handle_pc(new_pc).unwrap();

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

  pub fn peek_issue(&mut self, idx: u32) -> anyhow::Result<()> {
    let se = self.to_rtl_queue.front_mut().unwrap();
    if se.is_vfence_insn || se.is_exit_insn {
      return Ok(());
    }

    se.is_issued = true;
    se.issue_idx = 0;

    info!(
      "SpikePeekIssue: idx={idx}, pc = {:#x}, inst = {}",
      se.pc, se.disasm
    );

    Ok(())
  }

  pub fn update_lsu_idx(&mut self, enq: u32) -> anyhow::Result<()> {
    let lsu_reqs: Vec<u32> = (0..MSHR).map(|i| ((enq >> i) & 1) as u32).collect();

    if let Some(se) = self
      .to_rtl_queue
      .iter_mut()
      .rev()
      .find(|se| se.is_issued && (se.is_load || se.is_store) && (se.lsu_idx == LSU_IDX_DEFAULT))
    {
      if let Some(index) = lsu_reqs.iter().position(|&req| req == 1) {
        se.lsu_idx = index as u8;
        info!(
          "SpikeUpdateLSUIdx: Instruction is allocated with pc: {:#x}, inst: {} and lsu_idx: {}",
          se.pc, se.disasm, index
        );
      } else {
        info!("SpikeUpdateLSUIdx: waiting for lsu request to fire");
      }
    }
    Ok(())
  }

  pub fn peek_vrf_write(
    &mut self,
    lane_idx: u32,
    vd: u32,
    offset: u32,
    mask: u32,
    data: u32,
    instruction: u32,
    is_load: bool,
  ) -> anyhow::Result<()> {
    let vlen_in_bytes = self.config.vlen / 8;
    let lane_number = self.config.dlen / 32;
    let record_idx_base = (vd * vlen_in_bytes + (lane_idx + lane_number * offset) * 4) as usize;

    if let Some(se) = self
      .to_rtl_queue
      .iter_mut()
      .rev()
      .find(|se| se.issue_idx == instruction as u8 && se.is_load == is_load)
    {
      info!("SpikeRecordRFAccesses: lane={lane_idx}, vd={vd}, offset={offset}, mask={mask:04b}, data={data:08x}, instruction={instruction}");

      add_rtl_write(
        se,
        mask,
        data,
        record_idx_base,
        lane_idx as usize,
        vd as usize,
        offset as usize,
      );

      return Ok(());
    }

    Err(anyhow::anyhow!("cannot find se with issue_idx={lane_idx}"))
  }

  pub fn peek_vrf_write_from_lsu(
    &mut self,
    lane: u32,
    vd: u32,
    offset: u32,
    mask: u32,
    data: u32,
    instruction: u32,
  ) -> anyhow::Result<()> {
    let lane_idx = lane.trailing_zeros();
    self.peek_vrf_write(lane_idx, vd, offset, mask, data, instruction, false)
  }

  pub fn peek_vrf_write_from_lane(
    &mut self,
    lane_idx: u32,
    vd: u32,
    offset: u32,
    mask: u32,
    data: u32,
    instruction: u32,
  ) -> anyhow::Result<()> {
    self.peek_vrf_write(lane_idx, vd, offset, mask, data, instruction, true)
  }

  pub fn peek_tl(&mut self, peek_tl: PeekTL) -> anyhow::Result<()> {
    let base_addr = peek_tl.addr;
    let size = peek_tl.size;
    let lsu_idx = (peek_tl.source & 3) as u8;
    if let Some(se) = self
      .to_rtl_queue
      .iter_mut()
      .rev()
      .find(|se| se.issue_idx == lsu_idx)
    {
			println!("peekTL: {peek_tl:?}");
      match peek_tl.opcode {
        Opcode::Get => {
          let mut actual_data = vec![0u8; size as usize];
          for offset in 0..size {
            let addr = base_addr + offset as u32;
            match se.mem_access_record.all_reads.get_mut(&addr) {
              Some(mem_read) => {
                let single_mem_read = &mem_read.reads[mem_read.num_completed_reads as usize];
                mem_read.num_completed_reads += 1;
                actual_data[offset as usize] = single_mem_read.val;
              }
              None => {
                // TODO: check if the cache line should be accessed
                warn!("ReceiveTLReq addr: {addr:08X} insn: {} send falsy data 0xDE for accessing unexpected memory", format!("{:x}", se.inst_bits));
                actual_data[offset as usize] = 0xDE; // falsy data
              }
            }
          }

          let channel = peek_tl.idx;
          let mask = peek_tl.mask;
          let source = peek_tl.source;
          let hex_actual_data = actual_data
            .iter()
            .fold(String::new(), |acc, x| acc + &format!("{:02X} ", x));
          info!("SpikeReceiveTLReq: <- receive rtl mem get req: channel={channel}, base_addr={base_addr:08X}, size={size}, mask={mask:b}, source={source}, return_data={hex_actual_data}");
        }
        _ => {
          panic!("not implemented")
        }
      }

      return Ok(());
    }

    Err(anyhow::anyhow!("cannot find se with issue_idx={lsu_idx}"))
  }
}
