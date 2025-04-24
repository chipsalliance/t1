use std::{fs::read_to_string, path::PathBuf};

use anyhow::{Context, bail};
use clap::Parser;
use serde::Deserialize;
use spike_rs::runner::SpikeArgs;
use tracing::Level;
use tracing_subscriber::{EnvFilter, FmtSubscriber};

mod t1emu;
mod t1rocketemu;
pub(crate) mod util;

#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
pub struct SimCheckerArgs {
  /// Path to sim result json
  #[arg(long)]
  pub sim_result: PathBuf,

  /// Path to the rtl event log file
  #[arg(long)]
  pub rtl_event_file: PathBuf,

  /// Path to the ELF file
  /// (override that in sim result json)
  #[arg(long)]
  pub elf_file: Option<PathBuf>,

  /// Override VLEN (debug only)
  #[arg(long)]
  pub vlen_override: Option<u32>,

  /// Override DLEN (debug only)
  #[arg(long)]
  pub dlen_override: Option<u32>,

  /// Override ISA (debug only)
  #[arg(long)]
  pub isa_override: Option<String>,

  /// Log level: trace, debug, info, warn, error
  #[arg(long, default_value = "info")]
  pub log_level: String,
}

// contains only fields used by t1-sim-checker
#[derive(Deserialize, Debug)]
struct SimResult {
  flavor: String,
  meta_vlen: u32,
  meta_dlen: u32,
  meta_isa: String,
  meta_elf_file: Option<PathBuf>,
  success: bool,
}

fn main() -> anyhow::Result<()> {
  let args = SimCheckerArgs::parse();

  init_logger(&args.log_level);

  let sim_result = read_to_string(&args.sim_result).context("in open sim result json file")?;
  let sim_result: SimResult =
    serde_json::from_str(&sim_result).context("in parsing sim result file")?;

  if !sim_result.success {
    bail!("online run is unsuccessful");
  }

  let vlen = args.vlen_override.unwrap_or(sim_result.meta_vlen);
  let dlen = args.dlen_override.unwrap_or(sim_result.meta_dlen);
  let isa = args.isa_override.as_ref().unwrap_or(&sim_result.meta_isa);
  let elf_file = if let Some(elf_file) = &args.elf_file {
    elf_file
  } else if let Some(elf_file) = &sim_result.meta_elf_file {
    elf_file
  } else {
    bail!("neither cmd args nor sim_result.json contains elf path");
  };

  let spike_args = SpikeArgs {
    elf_file: elf_file.clone(),
    rtl_event_file: Some(args.rtl_event_file.clone()),
    vlen,
    dlen,
    set: isa.clone(),
  };

  match sim_result.flavor.as_str() {
    "t1emu" => {
      t1emu::run_diff(&spike_args)?;
    }
    "t1rocketemu" => {
      t1rocketemu::run_diff(&spike_args)?;
    }
    _ => bail!(
      "unknown flavor '{}', expected 't1emu' or 't1rocketemu'",
      sim_result.flavor
    ),
  }

  Ok(())
}

fn init_logger(log_level: &str) {
  let log_level: Level = log_level.parse().unwrap();
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
}
