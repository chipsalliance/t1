use std::path::PathBuf;

use dpi_common::plusarg::PlusArgMatcher;

pub mod dpi;
pub mod drive;

pub(crate) struct OnlineArgs {
  /// Path to the ELF file
  pub elf_file: PathBuf,

  /// Path to the log file
  pub log_file: Option<PathBuf>,

  /// vlen config
  pub vlen: u32,

  /// dlen config
  pub dlen: u32,

  /// ISA config
  pub set: String,

  // default to max_commit_interval * vlen / dlen
  pub max_commit_interval: u64,
}

const MAX_COMMIT_INTERVAL_COEFFICIENT: u64 = 10_0000;

impl OnlineArgs {
  pub fn from_plusargs(matcher: &PlusArgMatcher) -> Self {
    let vlen = env!("DESIGN_VLEN").parse().unwrap();
    let dlen = env!("DESIGN_DLEN").parse().unwrap();
    let max_commit_interval_coefficient = matcher
      .try_match("t1_max_commit_interval_coefficient")
      .map(|x| x.parse().unwrap())
      .unwrap_or(MAX_COMMIT_INTERVAL_COEFFICIENT);
    let max_commit_interval = max_commit_interval_coefficient * ((vlen / dlen) as u64);

    Self {
      elf_file: matcher.match_("t1_elf_file").into(),
      log_file: matcher.try_match("t1_log_file").map(|x| x.into()),
      vlen,
      dlen,
      set: env!("SPIKE_ISA_STRING").parse().unwrap(),
      max_commit_interval,
    }
  }
}

// keep in sync with TestBench.ClockGen
// the value is measured in simulation time unit
pub const CYCLE_PERIOD: u64 = 20000;

/// get cycle
pub fn get_t() -> u64 {
  svdpi::get_time() / CYCLE_PERIOD
}
