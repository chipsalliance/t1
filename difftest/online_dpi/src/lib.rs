use clap::Parser;
use common::CommonArgs;

pub mod dpi;
pub mod drive;

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

// keep in sync with TestBench.ClockGen
pub const CYCLE_PERIOD: u64 = 20;

/// get cycle
pub fn get_t() -> u64 {
  svdpi::get_time() / CYCLE_PERIOD
}

