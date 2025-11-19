use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum PokedexLog {
    Reset { pc: u32 },
    Exit { code: u32 },
    Commit(CommitLog),
    // TODO: investigate what should be recorded in exception
    Exception(CommitLog),
}

#[derive(Debug, Serialize, Deserialize)]
pub struct CommitLog {
    pub pc: u32,
    pub is_compressed: bool,
    pub instruction: u32,
    pub states_changed: Vec<StateWrite>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "dest", rename_all = "lowercase")]
pub enum StateWrite {
    Xrf { rd: u8, value: u32 },
    Frf { rd: u8, value: u32 },
    Vrf { rd: u8, value: Vec<u8> },
    Csr { name: String, value: u32 },
    // TODO : record load/store
    // Load { addr: u32 },
    // Store { addr: u32, data: Vec<u8> },
}
