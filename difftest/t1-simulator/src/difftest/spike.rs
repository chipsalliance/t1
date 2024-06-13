use lazy_static::lazy_static;
use std::collections::{HashMap, VecDeque};
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

const LSU_IDX_DEFAULT: u8 = 0xff;
const DATAPATH_WIDTH_IN_BYTES: usize = 8; // 8 = config.datapath_width_in_bytes = beatbyte = lsuBankParameters(0).beatbyte for blastoise

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
    if let ProgramHeader::Ph32(ph) = ph {
      if ph.get_type() == Ok(Type::Load) {
        let offset = ph.offset as usize;
        let size = ph.file_size as usize;
        let addr = ph.virtual_addr as usize;

        let slice = &buffer[offset..offset + size];
        ld(addr, size, slice.to_vec()).unwrap();
      }
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

#[derive(Debug, Clone)]
pub struct TLReqRecord {
  cycle: usize,
  size_by_byte: usize,
  addr: u32,

  muxin_read_required: bool,

  // For writes, as soon as the transaction is sent to the controller, the request is resolved, so we don't have to track the number
  //   of bytes that have been processed by the memory controller

  // Only meaningful for writes, this is the number of bytes written by user.
  bytes_received: usize,

  // This is the number of bytes(or worth-of transaction for reads) sent to the memory controller
  bytes_committed: usize,

  // This is the number of bytes that have been processed by the memory controller
  bytes_processed: usize,

  // For read, number of bytes returned to user
  bytes_returned: usize,

  op: Opcode,
}

impl TLReqRecord {
  pub fn new(cycle: usize, size_by_byte: usize, addr: u32, op: Opcode, burst_size: usize) -> Self {
    TLReqRecord {
      cycle,
      size_by_byte,
      addr,
      muxin_read_required: op == Opcode::PutFullData && size_by_byte < burst_size,
      bytes_received: 0,
      bytes_committed: 0,
      bytes_processed: 0,
      bytes_returned: 0,
      op,
    }
  }

  fn skip(&mut self) {
    self.muxin_read_required = false;
    self.bytes_committed = if self.op == Opcode::PutFullData {
      self.bytes_received
    } else {
      self.size_by_byte
    };
    self.bytes_processed = self.bytes_committed;
  }

  fn commit_tl_respones(&mut self, tl_bytes: usize) -> anyhow::Result<()> {
    self.bytes_returned += tl_bytes;

    Ok(())
  }

  fn done_return(&mut self) -> anyhow::Result<bool> {
    if self.muxin_read_required {
      return Ok(false);
    }
    if self.op == Opcode::PutFullData {
      Ok(self.bytes_returned > 0)
    } else {
      Ok(self.bytes_returned >= self.size_by_byte)
    }
  }
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

  /// tl request record of bank, indexed by issue_idx
  pub tl_req_record_of_bank: Vec<HashMap<usize, TLReqRecord>>,

  /// the get_t() of a req response waiting for ready
  pub tl_req_waiting_ready: Vec<Option<usize>>,

  /// the get_t() of a req with ongoing burst
  pub tl_req_ongoing_burst: Vec<Option<usize>>,

  /// implement the get_t() for mcycle csr update
  pub cycle: usize,

  /// for mcycle csr update
  pub spike_cycle: usize,
}

impl SpikeHandle {
  pub fn new(size: usize, fname: &Path, vlen: u32, dlen: u32, set: String) -> Self {
    // register the addr_to_mem callback
    unsafe { spike_register_callback(rs_addr_to_mem) }

    // create a new spike memory instance
    init_memory(size);

    // load the elf file
    let entry_addr = load_elf(fname).unwrap();

    // initialize spike
    let arch = &format!("vlen:{vlen},elen:32");
    let lvl = "M";

    let spike = Spike::new(arch, &set, lvl, (dlen / 32) as usize);

    // initialize processor
    let proc = spike.get_proc();
    let state = proc.get_state();
    proc.reset();
    state.set_pc(entry_addr);

    SpikeHandle {
      spike,
      to_rtl_queue: VecDeque::new(),
      config: Config { vlen, dlen },
      // config.tl_bank_number = 13
      tl_req_record_of_bank: (0..13).map(|_| HashMap::new()).collect(),
      tl_req_waiting_ready: vec![None; 13],
      tl_req_ongoing_burst: vec![None; 13],
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

  pub fn peek_tl(&mut self, peek_tl: &PeekTLEvent) -> anyhow::Result<()> {
    // config.tl_bank_number
    assert!(peek_tl.idx < 13);
    self.receive_tl_d_ready(peek_tl).unwrap();
    self.receive_tl_req(peek_tl).unwrap();
    Ok(())
  }

  pub fn receive_tl_d_ready(&mut self, peek_tl: &PeekTLEvent) -> anyhow::Result<()> {
    let idx = peek_tl.idx as usize;
    if !peek_tl.dready {
      return Ok(());
    }

    if let Some(addr) = self.tl_req_waiting_ready[idx] {
      let req_record = self.tl_req_record_of_bank[idx]
        .get_mut(&addr)
        .unwrap_or_else(|| panic!("cannot find current request with addr {addr:08X}"));

      req_record
        .commit_tl_respones(DATAPATH_WIDTH_IN_BYTES)
        .unwrap();

      if req_record.done_return().unwrap() {
        info!(
          "ReceiveTlDReady channel: {idx}, addr: {addr:08x}, -> tl response for {} reaches d_ready",
          match req_record.op {
            Opcode::Get => "Get",
            _ => "PutFullData",
          }
        );
      }

      self.tl_req_waiting_ready[idx] = None;

      // TODO(Meow): add this check back
      // panic!(format!("unknown opcode {}", req_record.op as i32));
    }
    Ok(())
  }
  // the info in peek tl should have cycle to debug
  pub fn receive_tl_req(&mut self, peek_tl: &PeekTLEvent) -> anyhow::Result<()> {
    let idx = peek_tl.idx as usize;
    let tl_data = peek_tl.data;
    let mask = peek_tl.mask;
    let cycle = peek_tl.cycle;
    let size = peek_tl.size;
    let source = peek_tl.source;
    let base_addr = peek_tl.addr;
    let lsu_idx = (peek_tl.source & 3) as u8;
    if let Some(se) = self
      .to_rtl_queue
      .iter_mut()
      .find(|se| se.lsu_idx == lsu_idx)
    {
      match peek_tl.opcode {
        Opcode::Get => {
          let mut actual_data = vec![0u8; size];
          for (offset, actual) in actual_data.iter_mut().enumerate().take(size) {
            let addr = base_addr + offset as u32;
            match se.mem_access_record.all_reads.get_mut(&addr) {
              Some(mem_read) => {
                *actual = mem_read.reads[mem_read.num_completed_reads].val;
                mem_read.num_completed_reads += 1;
              }
              None => {
                warn!("[{cycle}] ReceiveTLReq addr: {addr:08X} insn: {} send falsy data 0xDE for accessing unexpected memory", format!("{:x}", se.inst_bits));
                *actual = 0xDE; // falsy data
              }
            }
          }

          let hex_actual_data = actual_data
            .iter()
            .fold(String::new(), |acc, x| acc + &format!("{:02X} ", x));
          info!("[{cycle}] SpikeReceiveTLReq: <- receive rtl mem get req: channel={idx}, base_addr={base_addr:08X}, size={size}, mask={mask:b}, source={source}, return_data={hex_actual_data}");

          self.tl_req_record_of_bank[idx].insert(
            cycle,
            TLReqRecord::new(cycle, size, base_addr, Opcode::Get, 1),
          );

          self.tl_req_record_of_bank[idx]
            .get_mut(&cycle)
            .unwrap()
            .skip();
        }

        Opcode::PutFullData => {
          let mut cur_record: Option<&mut TLReqRecord> = None;
          // determine if it is a beat of ongoing burst
          // the first Some match the result of get, the second Some match the result determined by if the
          // tl_req_ongoing_burst[idx] is Some / None
          if let Some(tl_req_ongoing_burst) = self.tl_req_ongoing_burst[idx] {
            if let Some(record) = self.tl_req_record_of_bank[idx].get_mut(&tl_req_ongoing_burst) {
              if record.bytes_received < record.size_by_byte {
                assert_eq!(record.addr, base_addr, "inconsistent burst addr");
                assert_eq!(record.size_by_byte, size, "inconsistent burst size");
                info!(
                  "[{cycle}] ReceiveTLReq: continue burst, channel: {idx}, base_addr: {base_addr:08X}, offset: {}",
                  record.bytes_received
                );
                cur_record = Some(record);
              } else {
                panic!("[{cycle}] invalid record")
              }
            }
          }

          // else create a new record
          if cur_record.is_none() {
            // 1 is dummy value, won't be effective whatsoever. 1 is to ensure that no sub-line write is possible
            // here we do not use dramsim3.
            let record = TLReqRecord::new(cycle, size, base_addr, Opcode::PutFullData, 1);
            self.tl_req_record_of_bank[idx].insert(cycle, record);

            // record moved into self.tl_req_record_of_bank, so we should get it from there
            cur_record = self.tl_req_record_of_bank[idx].get_mut(&cycle);
          }

          let mut data = vec![0u8; size];
          let actual_beat_size = std::cmp::min(size, DATAPATH_WIDTH_IN_BYTES); // since tl require alignment
          let data_begin_pos = cur_record.as_ref().unwrap().bytes_received;

          // receive put data
          // if actual beat size is bigger than 8, there maybe some problems
          // TODO: fix this
          for offset in 0..actual_beat_size {
            data[data_begin_pos + offset] = (tl_data >> (offset * 8)) as u8;
          }
          info!("[{cycle}] RTLMemPutReq: <- receive rtl mem put req, channel: {idx}, base_addr: {base_addr:08X}, offset: {data_begin_pos}, size: {size}, source: {source:04X}, data: {tl_data:08X}, mask: {mask:04X}");

          // compare with spike event record
          for offset in 0..actual_beat_size {
            // config.datapath_width_in_bytes - 1 = 3
            let byte_lane_idx = (base_addr & 3) + offset as u32;
            // if byte_lane_idx > 32, there maybe some problem
            if (mask >> byte_lane_idx) & 1 != 0 {
              let byte_addr =
                base_addr + cur_record.as_ref().unwrap().bytes_received as u32 + offset as u32;
              let tl_data_byte = (tl_data >> (8 * byte_lane_idx)) as u8;
              let mem_write = se
                .mem_access_record
                .all_writes
                .get_mut(&byte_addr)
                .unwrap_or_else(|| {
                  panic!("[{cycle}] cannot find mem write of byte_addr {byte_addr:08x}")
                });
              assert!(
                mem_write.num_completed_writes < mem_write.writes.len(),
                "[{cycle}] written size:{} should be smaller than completed writes:{}",
                mem_write.writes.len(),
                mem_write.num_completed_writes
              );
              let single_mem_write_val = mem_write.writes[mem_write.num_completed_writes].val;
              mem_write.num_completed_writes += 1;
              assert_eq!(single_mem_write_val, tl_data_byte, "[{cycle}] expect mem write of byte {single_mem_write_val:02X}, actual byte {tl_data_byte:02X} (channel={idx}, byte_addr={byte_addr:08X}, pc = {:#x}, disasm = {})", se.pc, se.disasm);
            }
          }

          cur_record.as_mut().unwrap().bytes_received += actual_beat_size;
          cur_record.as_mut().unwrap().skip();

          // update tl_req_ongoing_burst
          if cur_record.as_ref().unwrap().bytes_received < size {
            self.tl_req_ongoing_burst[idx] = Some(cur_record.as_ref().unwrap().cycle);
          } else {
            self.tl_req_ongoing_burst[idx] = None;
          }
        }
        _ => {
          panic!("not implemented")
        }
      }

      return Ok(());
    }

    panic!("[{cycle}] cannot find se with lsu_idx={lsu_idx}")
  }
}
