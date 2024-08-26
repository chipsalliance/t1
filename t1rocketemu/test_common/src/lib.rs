use anyhow::Result;
use clap::Parser;
use spike_rs::Spike;
use std::path::PathBuf;
use tracing::Level;
use tracing_subscriber::{EnvFilter, FmtSubscriber};

pub mod rtl_config;
pub mod spike_runner;

#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
pub struct CommonArgs {
  /// Path to the ELF file
  #[arg(long)]
  pub elf_file: PathBuf,

  /// Path to the log file
  #[arg(long)]
  pub log_file: Option<PathBuf>,

  /// Log level: trace, debug, info, warn, error
  #[arg(long, default_value = "info")]
  pub log_level: String,

  /// vlen config
  #[arg(long)]
  pub vlen: u32,

  /// dlen config
  #[arg(long)]
  pub dlen: u32,

  /// ISA config
  #[arg(long)]
  pub set: String,
}

pub static MEM_SIZE: usize = 1usize << 32;

impl CommonArgs {
  pub fn to_spike_c_handler(&self) -> Box<Spike> {
    let lvl = "M";

    Spike::new(&self.set, lvl, (self.dlen / 32) as usize, MEM_SIZE)
  }

  pub fn setup_logger(&self) -> Result<()> {
    // setup log
    let log_level: Level = self.log_level.parse()?;
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
    Ok(())
  }
}
