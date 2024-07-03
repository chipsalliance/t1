use std::path::PathBuf;
use clap::Parser;
use anyhow::Result;
use tracing::Level;
use tracing_subscriber::{EnvFilter, FmtSubscriber};
use libspike_rs::Spike;

pub mod spike_runner;

#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
pub struct TestArgs {
  /// Path to the ELF file
  #[arg(short, long)]
  pub elf_file: PathBuf,

  /// Path to the log file
  #[arg(short, long)]
  pub log_file: Option<PathBuf>,

  /// Log level: trace, debug, info, warn, error
  #[arg(long, default_value = "info")]
  pub log_level: String,

  /// vlen config (default blastoise 512)
  #[arg(long)]
  pub vlen: u32,

  /// dlen config (default blastoise 256)
  #[arg(long)]
  pub dlen: u32,

  /// ISA config
  #[arg(long, default_value = "rv32gcv")]
  pub set: String,
}

static MEM_SIZE: usize = 1usize << 32;

impl TestArgs {
  pub fn to_spike_c_handler(&self) -> Box<Spike> {
    let arch = &format!("vlen:{},elen:32", self.vlen);
    let lvl = "M";

    Spike::new(
      arch,
      &self.set,
      lvl,
      (self.dlen / 32) as usize,
      MEM_SIZE,
    )
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
