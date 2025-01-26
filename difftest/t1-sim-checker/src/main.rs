use std::{fs::read_to_string, path::PathBuf};

use anyhow::{bail, Context};
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
pub struct OfflineArgs {
  /// Path to perf json
  #[arg(long)]
  pub perf_json: PathBuf,

  /// Path to the rtl event log file
  #[arg(long)]
  pub log_file: PathBuf,

  /// Path to the ELF file
  /// (override that in perf.json)
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

// contains only fields used by offline
#[derive(Deserialize, Debug)]
struct PerfJsonData {
  flavor: String,
  meta_vlen: u32,
  meta_dlen: u32,
  meta_isa: String,
  meta_elf_file: Option<PathBuf>,
  success: bool,
}

fn main() -> anyhow::Result<()> {
  let args = OfflineArgs::parse();

  init_logger(&args.log_level);

  let perf_json = read_to_string(&args.perf_json).context("in open perf json file")?;
  let perf_json: PerfJsonData =
    serde_json::from_str(&perf_json).context("in parsing perf json file")?;

  if !perf_json.success {
    bail!("online run is unsuccessful");
  }

  let vlen = args.vlen_override.unwrap_or(perf_json.meta_vlen);
  let dlen = args.dlen_override.unwrap_or(perf_json.meta_dlen);
  let isa = args.isa_override.as_ref().unwrap_or(&perf_json.meta_isa);
  let elf_file = if let Some(elf_file) = &args.elf_file {
    elf_file
  } else if let Some(elf_file) = &perf_json.meta_elf_file {
    elf_file
  } else {
    bail!("neither cmd args nor perf.json contains elf path");
  };

  let spike_args = SpikeArgs {
    elf_file: elf_file.clone(),
    log_file: Some(args.log_file.clone()),
    vlen,
    dlen,
    set: isa.clone(),
  };

  match perf_json.flavor.as_str() {
    "t1emu" => {
      t1emu::run_diff(&spike_args)?;
    }
    "t1rocketemu" => {
      t1rocketemu::run_diff(&spike_args)?;
    }
    _ => bail!(
      "unknown flavor '{}', expected 't1emu' or 't1rocketemu'",
      perf_json.flavor
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
