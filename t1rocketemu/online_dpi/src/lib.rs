use clap::Parser;
use common::CommonArgs;

pub mod dpi;
pub mod drive;
pub mod svdpi;
#[cfg(feature = "svvpi")]
pub mod svvpi;

#[derive(Parser)]
pub(crate) struct OfflineArgs {
  #[command(flatten)]
  pub common_args: CommonArgs,

  #[cfg(feature = "trace")]
  #[arg(long)]
  pub wave_path: String,

  #[cfg(feature = "trace")]
  #[arg(long, default_value = "")]
  pub dump_range: String,

  #[arg(long, default_value_t = 1000000)]
  pub timeout: u64,
}

// quit signal
const EXIT_POS: u32 = 0x4000_0000;
const EXIT_CODE: u32 = 0xdead_beef;

// keep in sync with TestBench.ClockGen
pub const CYCLE_PERIOD: u64 = 20;

/// get cycle
#[cfg(any(feature = "sv2023", feature = "svvpi"))]
pub fn get_t() -> u64 {
  get_time() / CYCLE_PERIOD
}

#[cfg(feature = "sv2023")]
pub fn get_time() -> u64 {
  svdpi::get_time()
}

#[cfg(all(not(feature = "sv2023"), feature = "svvpi"))]
pub fn get_time() -> u64 {
  svvpi::get_time()
}