use std::path::PathBuf;
use crate::dpi::verilator_main_wrapped;
use clap::Parser;
use common::CommonArgs;

mod dpi;
mod drive;

#[derive(Parser)]
pub(crate) struct OfflineArgs {
  #[command(flatten)]
  pub common_args: CommonArgs,

  #[arg(long)]
  pub wave_path: String,

  #[arg(long, default_value_t = 1000000)]
  pub timeout: u64,
}

fn main() {
  verilator_main_wrapped();
}
