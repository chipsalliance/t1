use std::fmt::Display;

// TODO: share library
#[derive(Debug, PartialEq, Eq, serde::Deserialize)]
#[serde(tag = "dest", rename_all = "lowercase")]
pub(crate) enum ModelStateWrite {
    Xrf { rd: u8, value: u32 },
    Frf { rd: u8, value: u32 },
    Csr { idx: u32, name: String, value: u32 },
    Load { addr: u32 },
    Store { addr: u32, data: Vec<u8> },
    ResetVector { pc: u32 },
}

impl Display for ModelStateWrite {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        use ModelStateWrite::*;

        match self {
            Xrf { rd, value } => write!(f, "[x{rd} <- {value:#010x}]"),
            Frf { rd, value } => write!(f, "[f{rd} <- {value:#010x}]"),
            Csr { idx, name, value } => write!(f, "[csr {idx} {name} <- {value:#010x}]"),
            Load { addr } => write!(f, "[mem {addr} -> load]"),
            Store { addr, data } => write!(f, "[mem {addr} <- {:x?}]", &data),
            ResetVector { pc } => write!(f, "[reset <- {pc}]"),
        }
    }
}

#[derive(Debug, PartialEq, Eq, serde::Deserialize)]
pub struct InsnCommit {
    pub pc: u64,
    pub instruction: u32,
    pub is_compressed: bool,
    pub states_changed: Vec<ModelStateWrite>,
}

impl InsnCommit {
    pub fn expect_exists<P>(&self, predicate: P) -> bool
    where
        P: FnMut(&ModelStateWrite) -> bool,
    {
        self.states_changed.iter().any(predicate)
    }

    pub fn find_reset_vector(&self) -> Option<u32> {
        self.states_changed.iter().find_map(|evt| match evt {
            ModelStateWrite::ResetVector { pc } => Some(*pc),
            _ => None,
        })
    }
}

impl Display for InsnCommit {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "PC={:#010x} instruction={:#010x} is_c_insn={} {}",
            self.pc,
            self.instruction,
            self.is_compressed,
            self.states_changed
                .iter()
                .map(|ev| format!("{ev} "))
                .collect::<String>()
        )
    }
}
