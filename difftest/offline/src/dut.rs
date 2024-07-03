use std::io::BufRead;
use std::path::Path;

use crate::json_events::JsonEvents;

#[derive(Debug)]
pub struct Dut {
  events: Vec<JsonEvents>,
  idx: u32,
}

impl Dut {
  fn read_json(path: &Path) -> anyhow::Result<Vec<JsonEvents>> {
    let file = std::fs::File::open(path).unwrap();
    let reader = std::io::BufReader::new(file);

    let mut events = Vec::new();

    for line in reader.lines() {
      let line = line.expect("line read error");
      let event: JsonEvents = serde_json::from_str(&line)?;
      events.push(event);
    }

    Ok(events)
  }

  pub fn new(path: &Path) -> Self {
    let events = Self::read_json(path).unwrap();
    let idx = 0;
    Self { events, idx }
  }

  pub fn step(&mut self) -> anyhow::Result<&JsonEvents> {
    let event = match self.events.get(self.idx as usize) {
      Some(event) => event,
      None => return Err(anyhow::anyhow!("no more events")),
    };
    self.idx += 1;

    Ok(event)
  }
}
