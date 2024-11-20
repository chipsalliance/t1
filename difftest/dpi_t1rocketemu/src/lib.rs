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

  // default to TIMEOUT_DEFAULT
  pub timeout: u64,
}

const TIMEOUT_DEFAULT: u64 = 100000000;

impl OnlineArgs {
  pub fn from_plusargs(matcher: &PlusArgMatcher) -> Self {
    Self {
      elf_file: matcher.match_("t1_elf_file").into(),
      dlen: env!("DESIGN_DLEN").parse().unwrap(),
      timeout: matcher
        .try_match("t1_timeout")
        .map(|x| x.parse().unwrap())
        .unwrap_or(TIMEOUT_DEFAULT),
    }
  }
}

// quit signal
pub const EXIT_POS: u32 = 0x4000_0000;
pub const EXIT_CODE: u32 = 0xdead_beef;

// keep in sync with TestBench.ClockGen
// the value is measured in simulation time unit
pub const CYCLE_PERIOD: u64 = 20000;

/// get cycle
pub fn get_t() -> u64 {
  svdpi::get_time() / CYCLE_PERIOD
}
