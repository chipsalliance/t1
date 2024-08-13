use std::path::Path;
use tracing::debug;

use spike_rs::spike_event::SpikeEvent;
use spike_rs::util::load_elf;
use spike_rs::Spike;

use crate::CommonArgs;

pub struct SpikeRunner {
  spike: Box<Spike>,

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
  pub fn new(args: &mut CommonArgs, do_log_vrf: bool) -> Self {
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
  pub fn spike_step(&mut self) -> SpikeEvent {
    let spike = &self.spike;
    let proc = self.spike.get_proc();
    let state = proc.get_state();

    state.set_mcycle((self.cycle + self.spike_cycle) as usize);

    let mut event = SpikeEvent::new(spike, self.do_log_vrf);
    state.clear();

    // inst is scalar
    debug!("SpikeStep: spike run scalar insn ({})", event.describe_insn());
    let new_pc = proc.func();
    event.log_mem_write(spike).unwrap();
    event.log_reg_write(spike).unwrap();

    state.handle_pc(new_pc).unwrap();

    self.spike_cycle += 1;

    event
  }
}
