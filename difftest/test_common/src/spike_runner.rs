use std::collections::VecDeque;
use std::path::Path;
use tracing::info;

use libspike_rs::spike_event::SpikeEvent;
use libspike_rs::util::load_elf;
use libspike_rs::{clip, Spike};

use crate::TestArgs;

pub struct SpikeRunner {
  spike: Box<Spike>,

  /// to rtl stack
  /// in the spike thread, spike should detech if this queue is full, if not
  /// full, execute until a vector instruction, record the behavior of this
  /// instruction, and send to str_stack. in the RTL thread, the RTL driver will
  /// consume from this queue, drive signal based on the queue. size of this
  /// queue should be as big as enough to make rtl free to run, reducing the
  /// context switch overhead.
  pub to_rtl_queue: VecDeque<SpikeEvent>,

  /// config for v extension
  pub vlen: u32,
  pub dlen: u32,

  /// implement the get_t() for mcycle csr update
  pub cycle: usize,

  /// for mcycle csr update
  pub spike_cycle: usize,

  pub do_log_vrf: bool,
}

impl SpikeRunner {
  pub fn new(args: &TestArgs, do_log_vrf: bool) -> Self {
    // load the elf file
    // initialize spike
    let mut spike = args.to_spike_c_handler();

    let entry_addr = load_elf(&mut spike, Path::new(&args.elf_file)).unwrap();

    // initialize processor
    let proc = spike.get_proc();
    let state = proc.get_state();
    proc.reset();
    state.set_pc(entry_addr);

    SpikeRunner {
      spike,
      to_rtl_queue: VecDeque::new(),
      vlen: args.vlen,
      dlen: args.dlen,
      cycle: 0,
      spike_cycle: 0,
      do_log_vrf,
    }
  }

  pub fn load_elf(&mut self, fname: &Path) -> anyhow::Result<u64> {
    load_elf(&mut *self.spike, fname)
  }

  // just execute one instruction for non-difftest
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
  fn spike_step(&mut self) -> Option<SpikeEvent> {
    let proc = self.spike.get_proc();
    let state = proc.get_state();

    state.set_mcycle(self.cycle + self.spike_cycle);

    let pc = state.get_pc();
    let disasm = proc.disassemble();

    let mut event = self.create_spike_event();
    state.clear();

    let new_pc = match event {
      // inst is load / store / v / quit
      Some(ref mut se) => {
        info!(
          "[{}] SpikeStep: spike run vector insn, pc={:#x}, disasm={:?}, spike_cycle={:?}",
          self.cycle, pc, disasm, self.spike_cycle
        );
        se.pre_log_arch_changes(&self.spike, self.vlen).unwrap();
        let new_pc_ = proc.func();
        se.log_arch_changes(&self.spike, self.vlen).unwrap();
        new_pc_
      }
      None => {
        info!(
          "[{}] SpikeStep: spike run scalar insn, pc={:#x}, disasm={:?}, spike_cycle={:?}",
          self.cycle, pc, disasm, self.spike_cycle
        );
        proc.func()
      }
    };

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

    //
    let is_load_type = opcode == 0b0000111 && (width.wrapping_sub(1) & 0b100 != 0);
    let is_store_type = opcode == 0b0100111 && (width.wrapping_sub(1) & 0b100 != 0);
    let is_v_type = opcode == 0b1010111;

    let is_csr_type = opcode == 0b1110011 && ((width & 0b011) != 0);
    let is_csr_write = is_csr_type && (((width & 0b100) | rs1) != 0);

    let is_quit = is_csr_write && csr == 0x7cc;

    if is_load_type || is_store_type || is_v_type || is_quit {
      return SpikeEvent::new(spike, self.do_log_vrf);
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

  /// returns none means exit
  pub fn pop_se_to_issue(&mut self) -> Option<SpikeEvent> {
    loop {
      let se = self.find_se_to_issue();
      if (se.is_vfence_insn() || se.is_exit_insn()) && self.to_rtl_queue.len() == 1 {
        if se.is_exit_insn() {
          return None;
        }
        // for fence-like instruction, consume them when possible
        self.to_rtl_queue.pop_back();
      } else {
        break Some(se);
      }
    }
  }
}
