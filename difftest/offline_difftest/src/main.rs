mod spike;

use clap::Parser;
use spike::SpikeHandle;
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

  /// count start of instruction trace
  #[arg(short, long, default_value = "0")]
  start: u64,

  /// count step of instruction trace
  #[arg(short, long, default_value = "100000")]
  step: u64,
}

fn main() -> anyhow::Result<()> {
  let global_logger = FmtSubscriber::builder()
    .with_env_filter(EnvFilter::from_default_env())
    .with_max_level(Level::TRACE)
    .without_time()
    .with_target(false)
    .compact()
    .finish();
  tracing::subscriber::set_global_default(global_logger)
    .expect("internal error: fail to setup log subscriber");

  let args = Args::parse();

  // count the instruction
  let mut count: u64 = 0;

  // if there is no log file, just run spike and quit
  let spike = SpikeHandle::new(1usize << 32, Path::new(&args.elf_file));
  loop {
    count += 1;
    if count > args.start && count % args.step == 0 {
      info!(
        "count = {}, pc = {:#x}, inst = {}",
        count,
        spike.get_pc(),
        spike.get_disasm()
      );
    }

    match spike.exec() {
      Ok(_) => {}
      Err(e) => {
        info!("total instrucions count = {}", count);
        info!("Simulation quit with error/quit: {:?}", e);
        return Ok(());
      }
    }
  }
}
