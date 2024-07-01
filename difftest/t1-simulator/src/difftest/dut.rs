use num_bigint::BigUint;
use serde::{Deserialize, Deserializer};
use std::io::BufRead;
use std::path::Path;
use std::str::FromStr;

fn bigint_to_vec_u8<'de, D>(deserializer: D) -> Result<Option<Vec<u8>>, D::Error>
where
  D: Deserializer<'de>,
{
  let opt: Option<&str> = Option::deserialize(deserializer)?;
  match opt {
    Some(s) => {
      let bigint =
        BigUint::from_str(s.trim_start_matches(' ')).map_err(serde::de::Error::custom)?;
      Ok(Some(bigint.to_bytes_le()))
    }
    None => Ok(None),
  }
}

fn bigint_to_vec_bool<'de, D>(deserializer: D) -> Result<Option<Vec<bool>>, D::Error>
where
  D: Deserializer<'de>,
{
  let opt: Option<&str> = Option::deserialize(deserializer)?;
  match opt {
    Some(s) => {
      let bigint =
        BigUint::from_str(s.trim_start_matches(' ')).map_err(serde::de::Error::custom)?;
      let bytes = bigint.to_bytes_le();
      let bools = bytes
        .iter()
        .flat_map(|byte| (0..8).map(move |i| (byte >> i) & 1 == 1))
        .collect();

      Ok(Some(bools))
    }
    None => Ok(None),
  }
}

#[derive(Deserialize, Debug)]
pub struct Parameter {
  pub idx: Option<u32>,
  pub enq: Option<u32>,
  pub opcode: Option<u32>,
  pub param: Option<u32>,
  pub size: Option<usize>,
  pub source: Option<u8>,
  pub address: Option<u32>,
  #[serde(deserialize_with = "bigint_to_vec_bool", default)]
  pub mask: Option<Vec<bool>>,
  #[serde(deserialize_with = "bigint_to_vec_u8", default)]
  pub data: Option<Vec<u8>>,
  pub corrupt: Option<u32>,
  pub dready: Option<u8>,
  pub vd: Option<u32>,
  pub offset: Option<u32>,
  pub instruction: Option<u8>,
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

pub struct MemoryWriteEvent {
  pub mask: Vec<bool>,
  pub data: Vec<u8>,
  pub source: u8,
  pub address: u32,
  pub cycle: usize,
}

pub struct VrfWriteEvent {
  pub idx: u32,
  pub vd: u32,
  pub offset: u32,
  pub mask: u8,
  pub data: u32,
  pub instruction: u8,
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
