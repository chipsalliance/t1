use crate::replay;
use num_bigint::BigUint;
use serde::{Deserialize, Deserializer};

fn str_to_u32<'de, D>(deserializer: D) -> Result<u32, D::Error>
where
    D: Deserializer<'de>,
{
    let s: &str = Deserialize::deserialize(deserializer)?;
    let value =
        u32::from_str_radix(s.trim_start_matches(' '), 16).map_err(serde::de::Error::custom)?;

    Ok(value)
}

fn str_to_vec_u8<'de, D>(deserializer: D) -> Result<Vec<u8>, D::Error>
where
    D: Deserializer<'de>,
{
    let s: &str = Deserialize::deserialize(deserializer)?;
    let s = s.trim_start();
    if s.len() % 2 != 0 {
        return Err(serde::de::Error::custom("Hex string length must be even"));
    }
    let bytes = (0..s.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(&s[i..i + 2], 16))
        .rev()
        .collect::<Result<Vec<u8>, _>>()
        .map_err(|_| serde::de::Error::custom("Failed to parse hex string into bytes"))?;
    Ok(bytes)
}

fn str_to_vec_bool<'de, D>(deserializer: D) -> Result<Vec<bool>, D::Error>
where
    D: Deserializer<'de>,
{
    let s: &str = Deserialize::deserialize(deserializer)?;
    let bigint = BigUint::parse_bytes(s.trim_start().as_bytes(), 16)
        .ok_or_else(|| serde::de::Error::custom("Failed to parse BigUint from hex string"))?;
    let bytes = bigint.to_bytes_le();
    let bools = bytes
        .iter()
        .flat_map(|byte| (0..8).map(move |i| (byte >> i) & 1u8 == 1u8))
        .collect();

    Ok(bools)
}

#[derive(Deserialize, Debug)]
#[serde(tag = "event")]
pub(crate) enum JsonEvent {
    RegWrite {
        idx: u8,
        #[serde(deserialize_with = "str_to_u32", default)]
        data: u32,
        cycle: u64,
    },
    RegWriteWait {
        idx: u8,
        cycle: u64,
    },
    FregWrite {
        idx: u8,
        #[serde(deserialize_with = "str_to_u32", default)]
        data: u32,
        cycle: u64,
    },
    FregWriteWait {
        idx: u8,
        cycle: u64,
    },
    Issue {
        idx: u8,
        cycle: u64,
    },
    LsuEnq {
        enq: u32,
        cycle: u64,
    },
    VrfWrite {
        issue_idx: u8,
        vrf_idx: usize,
        #[serde(deserialize_with = "str_to_vec_bool", default)]
        mask: Vec<bool>,
        #[serde(deserialize_with = "str_to_vec_u8", default)]
        data: Vec<u8>,
        cycle: u64,
    },
    MemoryWrite {
        #[serde(deserialize_with = "str_to_vec_bool", default)]
        mask: Vec<bool>,
        #[serde(deserialize_with = "str_to_vec_u8", default)]
        data: Vec<u8>,
        lsu_idx: u8,
        #[serde(deserialize_with = "str_to_u32", default)]
        address: u32,
        cycle: u64,
    },
    CheckRd {
        #[serde(deserialize_with = "str_to_u32", default)]
        data: u32,
        issue_idx: u8,
        cycle: u64,
    },
    VrfScoreboard {
        count: u32,
        issue_idx: u8,
        cycle: u64,
    },
}
