use anyhow::Context;
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

    for (i, line) in reader.lines().enumerate() {
      let line = line.expect("line read error");
      if line.starts_with("{") {
        // ignore illegal lines
        let event: JsonEvents = serde_json::from_str(&line)
          .with_context(|| format!("parsing {} line {}", path.display(), i + 1))?;
        events.push(event);
      }
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
