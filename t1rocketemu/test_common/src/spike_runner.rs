use std::collections::VecDeque;
use std::path::Path;
use tracing::debug;

use spike_rs::spike_event::SpikeEvent;
use spike_rs::util::load_elf;
use spike_rs::Spike;

use crate::CommonArgs;

pub struct SpikeRunner {
  spike: Box<Spike>,

  /// commit queue
  /// in the spike thread, spike should detech if this queue is full, if not
  /// full, execute until a vector instruction, record the behavior of this
  /// instruction, and send to commit queue.
  /// Note:
  /// - The event issued earliest is at the back of the queue
  /// - The queue may contain at most one unissued event. If so, the unissued event must be at the
  ///   front of the queue, and it must be a fence
  pub commit_queue: VecDeque<SpikeEvent>,

  /// vector queue to arrange the order of vector instructions, because of the register write
  /// dependency, the vector instruction should be issued in order.
  pub vector_queue: VecDeque<SpikeEvent>,

  /// vector queue to arrange the order of vector instructions, because of the register write
  /// dependency, the vector instruction should be issued in order.
  pub scalar_queue: VecDeque<SpikeEvent>,

  /// config for v extension
  pub vlen: u32,
  pub dlen: u32,

  /// implement the get_t() for mcycle csr update
  pub cycle: u64,

  /// for mcycle csr update
  pub spike_cycle: u64,

  pub do_log_vrf: bool,
}

impl SpikeRunner {
  pub fn new(args: &CommonArgs, do_log_vrf: bool) -> Self {
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
      commit_queue: VecDeque::new(),
      vector_queue: VecDeque::new(),
      scalar_queue: VecDeque::new(),
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

    Ok(())
  }

  // execute the spike processor for one instruction and record
  // the spike event for difftest
  pub fn spike_step(&mut self) -> SpikeEvent {
    let spike = &self.spike;
    let proc = self.spike.get_proc();
    let state = proc.get_state();

    let mcycle = (self.cycle + self.spike_cycle) as usize;
    state.set_mcycle(0);

    let mut event = SpikeEvent::new(spike, self.do_log_vrf);
    state.clear();

    let new_pc = if event.is_v() || event.is_exit() {
      // inst is v / quit
      debug!(
        "SpikeStep: spike run vector insn ({}), mcycle={mcycle}",
        event.describe_insn(),
      );
      event.pre_log_arch_changes(spike, self.vlen).unwrap();
      let new_pc_ = proc.func();
      event.log_arch_changes(spike, self.vlen).unwrap();
      new_pc_
    } else {
      // inst is scalar
      debug!(
        "SpikeStep: spike run scalar insn ({}), mcycle={mcycle}",
        event.describe_insn(),
      );
      let new_pc_ = proc.func();
      event.log_mem_write(spike).unwrap();
      event.log_reg_write(spike).unwrap();
      new_pc_
    };

    state.handle_pc(new_pc).unwrap();

    self.spike_cycle += 1;

    event
  }

  pub fn find_rf_se(&mut self) -> SpikeEvent {
    if !self.scalar_queue.is_empty() {
      // return the back (oldest) scalar insn
      self.scalar_queue.pop_back().unwrap()
    } else {
      // else, loop until find a se, and push the se to the front
      loop {
        let se = self.spike_step();
        if se.is_scalar() && se.is_rd_written && se.rd_idx != 0 {
          return se;
        } else if se.is_v() {
          self.vector_queue.push_front(se.clone());
        }
      }
    }
  }

  pub fn find_v_se(&mut self) -> SpikeEvent {
    if !self.vector_queue.is_empty() {
      // return the back (oldest) vector insn
      self.vector_queue.pop_back().unwrap()
    } else {
      // else, loop until find a se, and push the se to the front
      loop {
        let se = self.spike_step();
        if se.is_scalar() && se.is_rd_written && se.rd_idx != 0 {
          self.scalar_queue.push_front(se.clone());
        } else if se.is_v() {
          return se;
        }
      }
    }
  }
}
