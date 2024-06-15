use serde::Deserialize;
use std::io::BufRead;
use std::path::Path;

#[derive(Deserialize, Debug, PartialEq, Clone)]
pub enum Opcode {
  PutFullData = 0,
  PutPartialData = 1,
  Get = 4,
  // AccessAckData = 0,
  // AccessAck = 0,
}

#[derive(Deserialize, Debug)]
pub struct Parameter {
  pub idx: Option<u32>,
  pub enq: Option<u32>,
  pub opcode: Option<u32>,
  pub param: Option<u32>,
  pub size: Option<usize>,
  pub source: Option<u16>,
  pub address: Option<u32>,
  pub mask: Option<u32>,
  pub data: Option<u64>,
  pub corrupt: Option<u32>,
  pub dready: Option<u8>,
  pub vd: Option<u32>,
  pub offset: Option<u32>,
  pub instruction: Option<u32>,
  pub lane: Option<u32>,
  pub vxsat: Option<u32>,
  pub rd_valid: Option<u32>,
  pub rd: Option<u32>,
  pub mem: Option<u32>,
  pub cycle: Option<usize>,
}

#[derive(Deserialize, Debug)]
pub struct JsonEvents {
  pub event: String,
  pub parameter: Parameter,
}

pub struct IssueEvent {
  pub idx: u32,
  pub cycle: usize,
}

pub struct LsuEnqEvent {
  pub enq: u32,
  pub cycle: usize,
}

pub struct VrfWriteEvent {
  pub idx: u32,
  pub vd: u32,
  pub offset: u32,
  pub mask: u32,
  pub data: u64,
  pub instruction: u32,
  pub cycle: usize,
}

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
