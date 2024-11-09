mod difftest;
mod json_events;

use std::{
  fs::File,
  io::{BufRead, BufReader},
};

use anyhow::Context as _;
use clap::Parser;
use json_events::JsonEvents;
use tracing::info;

use spike_rs::runner::{SpikeArgs, SpikeRunner};

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

  match &args.log_file {
    None => {
      // if there is no log file, just run spike and quit
      run_spike(&args)
    }

    Some(log_file) => {
      // if there is a log file, run difftest
      let json_file = File::open(log_file)?;

      let mut runner = SpikeRunner::new(&args, true);
      let mut reader = JsonReader::new(BufReader::new(json_file));

      let mut event_count = 0;
      while let Some(event) = reader.next_event()? {
        event_count += 1;
        difftest::diff(&mut runner, &event)?;
      }

      eprintln!("Tototally {event_count} events processed");

      Ok(())
    }
  }
}

struct JsonReader<R: BufRead> {
  row: usize,
  reader: R,
}

impl<R: BufRead> JsonReader<R> {
  pub fn new(reader: R) -> Self {
    JsonReader { row: 0, reader }
  }

  pub fn next_event(&mut self) -> anyhow::Result<Option<JsonEvents>> {
    loop {
      let mut line = String::new();
      if self.reader.read_line(&mut line)? == 0 {
        return Ok(None);
      }

      self.row += 1;
      if !line.starts_with("{") {
        // ignore illegal lines
        continue;
      }

      let event: JsonEvents = serde_json::from_str(&line)
        .with_context(|| format!("json parsing error at row {}", self.row))?;

      return Ok(Some(event));
    }
  }
}
