use std::path::PathBuf;

use dpi_common::plusarg::PlusArgMatcher;

mod bus;
pub mod dpi;
pub mod drive;

pub(crate) struct OnlineArgs {
  /// Path to the ELF file
  pub elf_file: PathBuf,

  /// dlen config
  pub dlen: u32,

  // default to max_commit_interval
  pub max_commit_interval: u64,
}

const MAX_COMMIT_INTERVAL_COEFFICIENT: u64 = 10_0000;

impl OnlineArgs {
  pub fn from_plusargs(matcher: &PlusArgMatcher) -> Self {
    let max_commit_interval_coefficient = matcher
      .try_match("t1_max_commit_interval_coefficient")
      .map(|x| x.parse().unwrap())
      .unwrap_or(MAX_COMMIT_INTERVAL_COEFFICIENT);
    let max_commit_interval = max_commit_interval_coefficient;

    Self {
      elf_file: matcher.match_("t1_elf_file").into(),
      dlen: env!("DESIGN_DLEN").parse().unwrap(),
      max_commit_interval,
    }
  }
}

// quit signal
const EXIT_POS: u32 = 0x4000_0000;
const EXIT_CODE: u32 = 0xdead_beef;

// keep in sync with TestBench.ClockGen
// the value is measured in simulation time unit
pub const CYCLE_PERIOD: u64 = 20000;

/// get cycle
pub fn get_t() -> u64 {
  svdpi::get_time() / CYCLE_PERIOD
}
