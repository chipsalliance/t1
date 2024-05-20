use serde::Deserialize;
use std::io::BufRead;
use std::path::Path;

#[derive(Deserialize, Debug)]
pub enum Opcode {
  PutFullData = 0,
  PutPartialData = 1,
  Get = 4,
  // AccessAckData = 0,
  // AccessAck = 0,
}

impl Opcode {
  pub fn from_u32(n: u32) -> Self {
    match n {
      0 => Opcode::PutFullData,
      1 => Opcode::PutPartialData,
      4 => Opcode::Get,
      _ => panic!("unknown opcode"),
    }
  }
}

#[derive(Deserialize, Debug)]
pub struct Parameter {
  pub idx: Option<u32>,
  pub enq: Option<u32>,
  pub opcode: Option<u32>,
  pub param: Option<u32>,
  pub size: Option<u32>,
  pub source: Option<u32>,
  pub address: Option<u32>,
  pub mask: Option<u32>,
  pub data: Option<u32>,
  pub corrupt: Option<u32>,
  pub dReady: Option<u32>,
  pub vd: Option<u32>,
  pub offset: Option<u32>,
  pub instruction: Option<u32>,
  pub lane: Option<u32>,
  pub vxsat: Option<u32>,
  pub rd_valid: Option<u32>,
  pub rd: Option<u32>,
  pub mem: Option<u32>,
}

#[derive(Deserialize, Debug)]
pub struct JsonEvents {
  pub event: String,
  pub parameter: Parameter,
}

// pub trait Event {}

// pub struct Issue {
// 	pub idx: u32,
// }

pub struct PeekTL {
  pub idx: u32,
  pub opcode: Opcode,
  pub param: u32,
  pub size: u32,
  pub source: u32,
  pub addr: u32,
  pub mask: u32,
  pub data: u32,
  pub corrupt: u32,
  pub dready: u32,
}

// pub struct VrfWrite {
// 	pub idx: u32,
// 	pub vd: u32,
// 	pub offset: u32,
// 	pub mask: u32,
// 	pub data: u32,
// 	pub instruction: u32,
// }

// pub struct Inst {
// 	pub data: u32,
// 	pub vxsat: u32,
// 	pub rd_valid: u32,
// 	pub rd: u32,
// 	pub mem: u32,
// }

// pub struct MemoryWrite {
// 	pub idx: u32,
// 	pub vd: u32,
// 	pub offset: u32,
// 	pub mask: u32,
// 	pub data: u32,
// 	pub instruction: u32,
// 	pub lane: u32,
// }

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

    return Ok(event);
  }
}
