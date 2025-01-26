use std::io::BufRead;

use anyhow::Context as _;
use serde::Deserialize;

pub struct JsonReader<R: BufRead> {
  row: usize,
  reader: R,
}

impl<R: BufRead> JsonReader<R> {
  pub fn new(reader: R) -> Self {
    JsonReader { row: 0, reader }
  }

  pub fn next_event<EventType>(&mut self) -> anyhow::Result<Option<EventType>>
  where
    EventType: for<'a> Deserialize<'a>,
  {
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

      let event: EventType = serde_json::from_str(&line)
        .with_context(|| format!("json parsing error at row {}", self.row))?;

      return Ok(Some(event));
    }
  }
}
