mod difftest;
mod json_events;

use std::{fs::File, io::BufReader};

use anyhow::Context as _;
use tracing::info;

use spike_rs::runner::*;

use crate::util::JsonReader;

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

pub fn run_diff(spike_args: &SpikeArgs) -> anyhow::Result<()> {
  let rtl_event_path = spike_args.rtl_event_file.as_ref().unwrap();
  let json_file = File::open(rtl_event_path).context("in open rtl event file")?;

  let mut runner = SpikeRunner::new(&spike_args, true);
  let mut reader = JsonReader::new(BufReader::new(json_file));

  let mut event_count = 0;
  while let Some(event) = reader.next_event()? {
    event_count += 1;
    difftest::diff(&mut runner, &event)?;
  }

  eprintln!("Tototally {event_count} events processed");

  Ok(())
}
