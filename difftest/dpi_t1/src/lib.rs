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

  // default to TIMEOUT_DEFAULT
  pub timeout: u64,
}

const TIMEOUT_DEFAULT: u64 = 1000000;

impl OnlineArgs {
  pub fn from_plusargs(matcher: &PlusArgMatcher) -> Self {
    Self {
      elf_file: matcher.match_("t1_elf_file").into(),
      log_file: matcher.try_match("t1_log_file").map(|x| x.into()),

      vlen: env!("DESIGN_VLEN").parse().unwrap(),
      dlen: env!("DESIGN_DLEN").parse().unwrap(),
      set: env!("SPIKE_ISA_STRING").parse().unwrap(),
      timeout: matcher
        .try_match("t1_timeout")
        .map(|x| x.parse().unwrap())
        .unwrap_or(TIMEOUT_DEFAULT),
    }
  }
}

// keep in sync with TestBench.ClockGen
pub const CYCLE_PERIOD: u64 = 20;

/// get cycle
pub fn get_t() -> u64 {
  svdpi::get_time() / CYCLE_PERIOD
}
