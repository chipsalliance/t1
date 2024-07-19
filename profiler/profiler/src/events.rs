use serde::{Deserialize, Deserializer};

#[derive(Deserialize, Debug)]
pub struct PeekTL {}

#[derive(Deserialize, Debug)]
pub struct SimulationStart {}

#[derive(Deserialize, Debug)]
pub struct SimulationStop {
    pub reason: u8,
}

#[derive(Deserialize, Debug)]
pub struct Issue {
    pub idx: u8,
}

#[derive(Deserialize, Debug)]
pub struct LsuEnq {
    pub enq: u32,
}

#[derive(Deserialize, Debug)]
pub struct Inst {
    pub data: u32,
}

#[derive(Deserialize, Debug)]
pub struct VrfWrite {
    issue_idx: u8,
    vd: u32,
    offset: u32,
    #[serde(deserialize_with = "hex_to_vec_bool")]
    mask: Vec<bool>,
    #[serde(deserialize_with = "hex_to_vec_u8")]
    data: Vec<u8>,
    lane: u32,
}

#[derive(Deserialize, Debug)]
pub struct MemoryWrite {
    #[serde(deserialize_with = "hex_to_vec_bool")]
    mask: Vec<bool>,
    #[serde(deserialize_with = "hex_to_vec_u8")]
    data: Vec<u8>,
    lsu_idx: u8,
    #[serde(deserialize_with = "hex_to_u32")]
    address: u32,
}

#[derive(Deserialize, Debug)]
pub struct CheckRd {
    #[serde(deserialize_with = "hex_to_u32")]
    pub data: u32,
    pub issue_idx: u8,
}

#[derive(Deserialize, Debug)]
pub struct VrfScoreboardReport {
    pub count: u32,
    pub issue_idx: u8,
}

#[derive(Deserialize, Debug)]
#[serde(tag = "event")]
pub enum Event {
    SimulationStart(SimulationStart),
    SimulationStop(SimulationStop),
    Issue(Issue),
    LsuEnq(LsuEnq),
    VrfWrite(VrfWrite),
    MemoryWrite(MemoryWrite),
    CheckRd(CheckRd),
    VrfScoreboardReport(VrfScoreboardReport),
}

#[derive(Deserialize, Debug)]
pub struct EventWithTime {
    #[serde(flatten)]
    pub event: Event,
    pub cycle: u64,
}

fn hex_to_u32<'de, D>(deserializer: D) -> Result<u32, D::Error>
where
    D: Deserializer<'de>,
{
    let s: &str = Deserialize::deserialize(deserializer)?;
    let value =
        u32::from_str_radix(s.trim_start_matches(' '), 16).map_err(serde::de::Error::custom)?;

    Ok(value)
}

fn hex_to_vec_u8<'de, D>(deserializer: D) -> Result<Vec<u8>, D::Error>
where
    D: Deserializer<'de>,
{
    let s: &str = Deserialize::deserialize(deserializer)?;
    _hex_to_vec_u8(s.as_bytes()).ok_or_else(|| serde::de::Error::custom("invalid hex format"))
}

fn hex_to_vec_bool<'de, D>(deserializer: D) -> Result<Vec<bool>, D::Error>
where
    D: Deserializer<'de>,
{
    let s: &str = Deserialize::deserialize(deserializer)?;
    _hex_to_vec_bool(s.as_bytes()).ok_or_else(|| serde::de::Error::custom("invalid hex format"))
}

fn _hex_to_vec_u8(s: &[u8]) -> Option<Vec<u8>> {
    if s.len() % 2 != 0 {
        return None;
    }

    let mut data = Vec::with_capacity(s.len() % 2);
    for s in s.chunks_exact(2).rev() {
        let hi = hex_char_to_u8(s[0])?;
        let lo = hex_char_to_u8(s[1])?;
        data.push(hi * 16 + lo);
    }
    Some(data)
}

fn _hex_to_vec_bool(s: &[u8]) -> Option<Vec<bool>> {
    let mut mask = Vec::with_capacity(s.len() * 4);
    for &c in s.iter().rev() {
        let c = hex_char_to_u8(c)?;
        mask.push((c & 0x01) != 0);
        mask.push((c & 0x02) != 0);
        mask.push((c & 0x04) != 0);
        mask.push((c & 0x08) != 0);
    }
    Some(mask)
}

fn hex_char_to_u8(c: u8) -> Option<u8> {
    Some(match c {
        b'0'..=b'9' => c - b'0',
        b'a'..=b'f' => c - b'a' + 10,
        b'A'..=b'F' => c - b'A' + 10,
        _ => return None,
    })
}
