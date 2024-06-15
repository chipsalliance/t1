mod difftest;

use clap::Parser;
use difftest::Difftest;
use difftest::SpikeHandle;
use std::path::Path;
use tracing::{info, Level};
use tracing_subscriber::{EnvFilter, FmtSubscriber};

/// A simple offline difftest tool
#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
struct Args {
  /// Path to the ELF file
  #[arg(short, long)]
  elf_file: String,

  /// Path to the log file
  #[arg(short, long)]
  log_file: Option<String>,

  /// Log level: trace, debug, info, warn, error
  #[arg(short, long, default_value = "info")]
  log_level: String,

  /// vlen config (default blastoise 512)
  #[arg(short, long)]
  vlen: u32,

  /// dlen config (default blastoise 256)
  #[arg(short, long)]
  dlen: u32,

  /// ISA config
  #[arg(short, long, default_value = "rv32gcv")]
  set: String,
}

fn run_spike(args: Args) -> anyhow::Result<()> {
  let mut count: u64 = 0;

  let spike = SpikeHandle::new(
    1usize << 32,
    Path::new(&args.elf_file),
    args.vlen,
    args.dlen,
    args.set,
  );
  loop {
    count += 1;
    if count % 1000000 == 0 {
      info!("count = {}", count);
    }
    match spike.exec() {
      Ok(_) => {}
      Err(_) => {
        info!("total v instrucions count = {}", count);
        info!("Simulation quit graceful");
        return Ok(());
      }
    };
  }
}

fn main() -> anyhow::Result<()> {
  // parse args
  let args = Args::parse();

  // setup log
  let log_level: Level = args.log_level.parse()?;
  let global_logger = FmtSubscriber::builder()
    .with_env_filter(EnvFilter::from_default_env())
    .with_max_level(log_level)
    .without_time()
    .with_target(false)
    .compact()
    .finish();
  tracing::subscriber::set_global_default(global_logger)
    .expect("internal error: fail to setup log subscriber");

  // if there is no log file, just run spike and quit
  if args.log_file.is_none() {
    run_spike(args)?;
    return Ok(());
  }

  // if there is a log file, run difftest
  let mut diff = Difftest::new(
    1usize << 32,
    args.elf_file,
    args.log_file.unwrap(),
    args.vlen,
    args.dlen,
    args.set,
  );

  loop {
    match diff.diff() {
      Ok(_) => {}
      Err(e) => {
        info!("Simulation quit/error with {}", e);
        return Ok(());
      }
    }
  }
}
