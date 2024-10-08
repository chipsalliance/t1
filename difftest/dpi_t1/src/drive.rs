use dpi_common::dump::{DumpControl, DumpEndError};
use spike_rs::runner::SpikeRunner;
use spike_rs::runner::{SpikeArgs, MEM_SIZE};
use spike_rs::spike_event::MemAccessRecord;
use spike_rs::spike_event::SpikeEvent;
use spike_rs::util::load_elf_to_buffer;
use tracing::{debug, error, info, trace};

use crate::dpi::*;
use crate::get_t;
use crate::OnlineArgs;
use svdpi::SvScope;

struct ShadowMem {
  mem: Vec<u8>,
}

impl ShadowMem {
  pub fn new() -> Self {
    Self { mem: vec![0; MEM_SIZE] }
  }
  pub fn apply_writes(&mut self, records: &MemAccessRecord) {
    for (&addr, record) in &records.all_writes {
      if let Some(write) = record.writes.last() {
        self.mem[addr as usize] = write.val;
      }
    }
  }

  pub fn read_mem(&self, addr: u32, size: u32) -> &[u8] {
    let start = addr as usize;
    let end = (addr + size) as usize;
    &self.mem[start..end]
  }

  // size: 1 << arsize
  // bus_size: AXI bus width in bytes
  // return: Vec<u8> with len=bus_size
  // if size < bus_size, the result is padded due to AXI narrow transfer rules
  pub fn read_mem_axi(&self, addr: u32, size: u32, bus_size: u32) -> Vec<u8> {
    assert!(
      addr % size == 0 && bus_size % size == 0,
      "unaligned access addr={addr:#x} size={size}B dlen={bus_size}B"
    );

    let data = self.read_mem(addr, size);
    if size < bus_size {
      // narrow
      let mut data_padded = vec![0; bus_size as usize];
      let start = (addr % bus_size) as usize;
      let end = start + data.len();
      data_padded[start..end].copy_from_slice(data);

      data_padded
    } else {
      // normal
      data.to_vec()
    }
  }

  // size: 1 << awsize
  // bus_size: AXI bus width in bytes
  // masks: write strokes, len=bus_size
  // data: write data, len=bus_size
  pub fn write_mem_axi(
    &mut self,
    addr: u32,
    size: u32,
    bus_size: u32,
    masks: &[bool],
    data: &[u8],
  ) {
    assert!(
      addr % size == 0 && bus_size % size == 0,
      "unaligned write access addr={addr:#x} size={size}B dlen={bus_size}B"
    );

    // handle strb=0 AXI payload
    if !masks.iter().any(|&x| x) {
      trace!("Mask 0 write detect");
      return;
    }

    // TODO: we do not check strobe is compatible with (addr, awsize)
    let addr_align = addr & ((!bus_size) + 1);

    let bus_size = bus_size as usize;
    assert_eq!(bus_size, masks.len());
    assert_eq!(bus_size, data.len());

    for i in 0..bus_size {
      if masks[i] {
        self.mem[addr_align as usize + i] = data[i];
      }
    }
  }
}

pub(crate) struct Driver {
  spike_runner: SpikeRunner,

  // SvScope from t1_cosim_init
  scope: SvScope,

  dump_control: DumpControl,
  pub(crate) success: bool,

  pub(crate) dlen: u32,

  timeout: u64,

  // driver state
  last_commit_cycle: u64,
  issued: u64,
  vector_lsu_count: u8,

  shadow_mem: ShadowMem,
}

impl Driver {
  pub(crate) fn new(scope: SvScope, dump_control: DumpControl, args: &OnlineArgs) -> Self {
    let mut self_ = Self {
      spike_runner: SpikeRunner::new(
        &SpikeArgs {
          elf_file: args.elf_file.clone(),
          log_file: args.log_file.clone(),
          log_level: "info".to_string(),
          vlen: args.vlen,
          dlen: args.dlen,
          set: args.set.clone(),
        },
        false,
      ),

      scope,
      dump_control,
      success: false,

      dlen: args.dlen,
      timeout: args.timeout,
      last_commit_cycle: 0,

      issued: 0,
      vector_lsu_count: 0,
      shadow_mem: ShadowMem::new(),
    };
    self_.spike_runner.load_elf(&args.elf_file).unwrap();

    load_elf_to_buffer(&mut self_.shadow_mem.mem, &args.elf_file).unwrap();
    self_
  }

  pub(crate) fn axi_read_high_bandwidth(&mut self, addr: u32, arsize: u64) -> AxiReadPayload {
    let size = 1 << arsize;
    let data = self.shadow_mem.read_mem_axi(addr, size, self.dlen / 8);
    let data_hex = hex::encode(&data);
    trace!(
      "[{}] axi_read_high_bandwidth (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
    AxiReadPayload { data }
  }

  pub(crate) fn axi_write_high_bandwidth(
    &mut self,
    addr: u32,
    awsize: u64,
    strobe: &[bool],
    data: &[u8],
  ) {
    let size = 1 << awsize;

    self.shadow_mem.write_mem_axi(addr, size, self.dlen / 8, &strobe, data);
    let data_hex = hex::encode(data);
    trace!(
      "[{}] axi_write_high_bandwidth (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
  }

  pub(crate) fn axi_read_indexed(&mut self, addr: u32, arsize: u64) -> AxiReadPayload {
    let size = 1 << arsize;
    assert!(size <= 4);
    let data = self.shadow_mem.read_mem_axi(addr, size, 4);
    let data_hex = hex::encode(&data);
    trace!(
      "[{}] axi_read_indexed (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
    AxiReadPayload { data }
  }

  pub(crate) fn axi_write_indexed_access_port(
    &mut self,
    addr: u32,
    awsize: u64,
    strobe: &[bool],
    data: &[u8],
  ) {
    let size = 1 << awsize;
    self.shadow_mem.write_mem_axi(addr, size, 4, strobe, data);
    let data_hex = hex::encode(data);
    trace!(
      "[{}] axi_write_indexed_access_port (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
  }

  pub(crate) fn watchdog(&mut self) -> u8 {
    let tick = get_t();
    if tick - self.last_commit_cycle > self.timeout {
      error!(
        "[{}] watchdog timeout (last_commit_cycle={})",
        get_t(),
        self.last_commit_cycle
      );
      WATCHDOG_TIMEOUT
    } else {
      match self.dump_control.trigger_watchdog(tick) {
        Ok(()) => {}
        Err(DumpEndError) => {
          info!(
            "[{tick}] run to dump end, exiting (last_commit_cycle={})",
            self.last_commit_cycle
          );
          return WATCHDOG_TIMEOUT;
        }
      }

      trace!("[{}] watchdog continue", get_t());
      WATCHDOG_CONTINUE
    }
  }

  pub(crate) fn step(&mut self) -> SpikeEvent {
    // there will be a vfence / scalar load / scalar store in the commit queue's front
    if let Some(se) = self.spike_runner.commit_queue.front() {
      if se.is_vfence() || se.is_load() || se.is_store() {
        return se.clone();
      }
    }

    loop {
      // step until the instruction is a vector / exit / scalar load / scalar store
      // push into the commit queue and return
      let se = self.spike_runner.spike_step();
      if se.is_v() || se.is_vfence() || se.is_load() || se.is_store() {
        self.spike_runner.commit_queue.push_front(se.clone());
        return se;
      }
    }
  }

  pub(crate) fn issue_instruction(&mut self) -> IssueData {
    loop {
      let se = self.step();

      return if se.is_vfence() {
        if self.spike_runner.commit_queue.len() == 1 {
          // earlier instructions are committed
          if se.is_exit() {
            info!(
              "[{}] seeing an exit instruction on {:08x}, sending ISSUE_EXIT",
              get_t(),
              se.pc
            );
            self.success = true;
            IssueData { meta: ISSUE_EXIT, ..Default::default() }
          } else {
            self.spike_runner.commit_queue.pop_back();
            continue;
          }
        } else {
          debug!(
            "[{}] waiting for queue being cleared to issue the fence, len={}",
            get_t(),
            self.spike_runner.commit_queue.len()
          );
          // waiting for earlier instructions to be committed
          IssueData { meta: ISSUE_FENCE, ..Default::default() }
        }
      } else if se.is_load() || se.is_store() {
        // scalar load / scalar store
        trace!(
          "[{}] issue scalar ({}), count={}",
          get_t(),
          if se.is_load() { "load" } else { "store" },
          self.vector_lsu_count
        );
        if self.vector_lsu_count == 0 {
          // issue scalar load / store
          self.shadow_mem.apply_writes(&se.mem_access_record);
          self.spike_runner.commit_queue.pop_front();
          continue;
        } else {
          IssueData { meta: ISSUE_NOT_VALID, ..Default::default() }
        }
      } else {
        // vector
        if se.is_vload() || se.is_vstore() {
          self.vector_lsu_count += 1;
        }

        info!(
          "[{}] issue vector ({}), count={} ",
          get_t(),
          se.describe_insn(),
          self.vector_lsu_count,
        );
        self.issued += 1;

        IssueData {
          instruction_bits: se.inst_bits,
          src1_bits: se.rs1_bits,
          src2_bits: se.rs2_bits,
          vtype: se.vtype,
          vl: se.vl,
          vstart: se.vstart as u32,
          vcsr: se.vcsr(),
          meta: ISSUE_VALID,
        }
      };
    }
  }

  pub(crate) fn retire_instruction(&mut self, _: &Retire) {
    let se = self.spike_runner.commit_queue.back().unwrap();

    // todo: filter all vector instruction.
    self.shadow_mem.apply_writes(&se.mem_access_record);

    self.spike_runner.commit_queue.pop_back();
    self.last_commit_cycle = get_t();
  }

  pub(crate) fn retire_memory(&mut self) {
    self.vector_lsu_count -= 1;
    info!("[{}] retire, count={}", get_t(), self.vector_lsu_count);
  }
}
