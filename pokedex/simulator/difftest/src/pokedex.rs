use serde::Deserialize;
use std::fmt::Display;

pub type PokedexLog = Vec<PokedexEventKind>;

pub fn parse_from(raw: impl AsRef<[u8]>) -> PokedexLog {
    String::from_utf8_lossy(raw.as_ref())
        .lines()
        .enumerate()
        .map(|(line_number, line_str)| {
            serde_json::from_str::<PokedexEventKind>(line_str).unwrap_or_else(|err| {
                panic!("fail parsing pokedex log at line {line_number}: {err}")
            })
        })
        .collect()
}

#[allow(dead_code)]
#[derive(Debug, Deserialize)]
#[serde(tag = "event_type")]
pub enum PokedexEventKind {
    #[serde(rename = "physical_memory")]
    PhysicalMemory {
        action: String,
        bytes: u32,
        address: u64,
    },
    #[serde(rename = "csr")]
    Csr {
        action: String,
        pc: u32,
        csr_idx: u32,
        csr_name: String,
        data: u32,
    },
    #[serde(rename = "register")]
    Register {
        action: String,
        pc: u32,
        reg_idx: u8,
        data: u32,
    },
    #[serde(rename = "fp_register")]
    FpReg {
        action: String,
        pc: u32,
        reg_idx: u8,
        data: u32,
    },
    #[serde(rename = "instruction_fetch")]
    InstructionFetch { instruction: u32 },
    #[serde(rename = "reset_vector")]
    ResetVector { new_addr: u32 },
}

impl Display for PokedexEventKind {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Register {
                action,
                pc,
                reg_idx,
                data,
            } => indoc::writedoc!(
                f,
                "PC={pc:#010x} {action} to integer register [x{reg_idx}] with [{data:#010x}]"
            ),
            Self::FpReg {
                action,
                pc,
                reg_idx,
                data,
            } => indoc::writedoc!(
                f,
                "PC={pc:#010x} {action} to FP register [f{reg_idx}] with [{data:#010x}]"
            ),
            _ => write!(f, "{self:#?}"),
        }
    }
}

impl PokedexEventKind {
    pub fn get_reset_vector(&self) -> Option<u32> {
        match self {
            Self::ResetVector { new_addr } => Some(*new_addr),
            _ => None,
        }
    }

    pub fn get_pc(&self) -> Option<u32> {
        match self {
            Self::Csr { pc, .. } => Some(*pc),
            Self::Register { pc, .. } => Some(*pc),
            Self::FpReg { pc, .. } => Some(*pc),
            _ => None,
        }
    }
}
