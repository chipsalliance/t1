mod difftest;
mod dut;
mod json_events;

use clap::Parser;
use tracing::info;

use spike_rs::runner::*;

use crate::difftest::Difftest;

fn run_spike(args: &SpikeArgs) -> anyhow::Result<()> {
  let mut count: u64 = 0;

  let spike = SpikeRunner::new(args, true);
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
  let args = SpikeArgs::parse();

  args.setup_logger()?;

  // if there is no log file, just run spike and quit
  if args.log_file.is_none() {
    run_spike(&args)?;
    return Ok(());
  }

  // if there is a log file, run difftest
  let mut diff = Difftest::new(args);

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
