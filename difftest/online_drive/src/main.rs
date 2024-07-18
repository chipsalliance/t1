use crate::dpi::verilator_main;
use clap::Parser;
use common::CommonArgs;

mod dpi;
mod drive;

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

fn main() {
  verilator_main();
}
