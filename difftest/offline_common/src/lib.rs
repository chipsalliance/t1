use std::path::PathBuf;

use clap::Parser;
use spike_rs::runner::SpikeArgs;
use tracing::Level;
use tracing_subscriber::{EnvFilter, FmtSubscriber};

#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
pub struct OfflineArgs {
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
  #[arg(long, default_value = env!("DESIGN_VLEN"))]
  pub vlen: u32,

  /// dlen config
  #[arg(long, default_value = env!("DESIGN_DLEN"))]
  pub dlen: u32,

  /// ISA config
  #[arg(long, default_value = env!("SPIKE_ISA_STRING"))]
  pub set: String,
}

impl OfflineArgs {
  pub fn to_spike_args(self) -> SpikeArgs {
    SpikeArgs {
      elf_file: self.elf_file,
      log_file: self.log_file,
      vlen: self.vlen,
      dlen: self.dlen,
      set: self.set,
    }
  }

  pub fn setup_logger(&self) -> anyhow::Result<()> {
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
