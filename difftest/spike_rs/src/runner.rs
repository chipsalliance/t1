use clap::Parser;
use std::collections::VecDeque;
use std::path::{Path, PathBuf};
use tracing::{debug, Level};
use tracing_subscriber::{EnvFilter, FmtSubscriber};

use crate::spike_event::SpikeEvent;
use crate::util::load_elf;
use crate::Spike;

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

  /// config for v extension
  pub vlen: u32,
  pub dlen: u32,

  /// implement the get_t() for mcycle csr update
  pub cycle: u64,

  /// for mcycle csr update
  pub spike_cycle: u64,

  pub do_log_vrf: bool,
}

#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
pub struct SpikeArgs {
  /// Path to the ELF file
  #[arg(long)]
  pub elf_file: PathBuf,

  /// Path to the log file
  #[arg(long)]
  pub log_file: Option<PathBuf>,

  /// Log level: trace, debug, info, warn, error
  #[arg(long, default_value = "info")]
  pub log_level: String,

  /// vlen config
  #[arg(long, default_value = env!("DESIGN_VLEN"))]
  pub vlen: u32,

  /// dlen config
  #[arg(long, default_value = env!("DESIGN_DLEN"))]
  pub dlen: u32,

  /// ISA config
  #[arg(long, default_value = env!("SPIKE_ISA_STRING"))]
  pub set: String,
}

impl SpikeArgs {
  fn to_spike_c_handler(&self) -> Box<Spike> {
    let lvl = "M";
    Spike::new(&self.set, lvl, (self.dlen / 32) as usize, MEM_SIZE)
  }

  pub fn setup_logger(&self) -> anyhow::Result<()> {
    // setup log
    let log_level: Level = self.log_level.parse()?;
    let global_logger = FmtSubscriber::builder()
      .with_env_filter(EnvFilter::from_default_env())
      .with_max_level(log_level)
      .without_time()
      .with_target(false)
      .with_ansi(true)
      .compact()
      .finish();
    tracing::subscriber::set_global_default(global_logger)
      .expect("internal error: fail to setup log subscriber");

    Ok(())
  }
}

pub const MEM_SIZE: usize = 1usize << 32;

impl SpikeRunner {
  pub fn new(args: &SpikeArgs, do_log_vrf: bool) -> Self {
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
      new_pc_
    };

    state.handle_pc(new_pc).unwrap();

    self.spike_cycle += 1;

    event
  }

  pub fn find_v_se_to_issue(&mut self) -> SpikeEvent {
    if !self.commit_queue.is_empty() && self.commit_queue.front().unwrap().is_vfence() {
      // if the front (latest) se is a vfence, return the vfence
      self.commit_queue.front().unwrap().clone()
    } else {
      // else, loop until find a se, and push the se to the front
      loop {
        let se = self.spike_step();
        if se.is_v() {
          self.commit_queue.push_front(se.clone());
          break se.clone();
        }
      }
    }
  }
}
