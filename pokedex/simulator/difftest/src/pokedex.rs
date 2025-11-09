use std::fmt::Display;

use crate::replay;

// TODO: share library
#[derive(Debug, PartialEq, Eq, serde::Deserialize)]
#[serde(tag = "dest", rename_all = "lowercase")]
pub(crate) enum ModelStateWrite {
    Xrf { rd: u8, value: u32 },
    Frf { rd: u8, value: u32 },
    Vrf { rd: u8, value: Vec<u8> },
    Csr { name: String, value: u32 },
    Load { addr: u32 },
    Store { addr: u32, data: Vec<u8> },
    ResetVector { pc: u32 },
    Poweroff { exit_code: i32 },
}

impl Display for ModelStateWrite {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        use ModelStateWrite::*;

        match self {
            Xrf { rd, value } => write!(f, "[x{rd} <- {value:#010x}]"),
            Frf { rd, value } => write!(f, "[f{rd} <- {value:#010x}]"),
            Vrf { rd, value } => write!(f, "[v{rd} <- {value:02x?}]"),
            Csr { name, value } => write!(f, "[csr {name} <- {value:#010x}]"),
            Load { addr } => write!(f, "[mem {addr} -> load]"),
            Store { addr, data } => write!(f, "[mem {addr} <- {:x?}]", &data),
            ResetVector { pc } => write!(f, "[reset <- {pc:#010x}]"),
            Poweroff { exit_code } => write!(f, "poweroff -> {exit_code}"),
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

impl replay::IsInsnCommit for InsnCommit {
    fn get_pc(&self) -> u32 {
        self.pc as u32
    }

    fn write_cpu_state(&self, state: &mut replay::CpuState) -> replay::DiffRecord {
        state.pc = self.pc as u32;

        let mut dr = replay::DiffRecord::default();
        self.states_changed.iter().for_each(|write| {
            use ModelStateWrite::*;

            match write {
                &Xrf { rd, value } => {
                    state.write_gpr(rd as usize, value, &mut dr);
                }
                &Frf { rd, value } => {
                    state.write_fpr(rd as usize, value, &mut dr);
                }
                &Vrf { rd, ref value } => {
                    state.write_vreg(rd as usize, value, &mut dr);
                }
                &Csr { ref name, value } => {
                    // FIXME: error handling
                    state.write_csr(name, value).unwrap_or_else(|_| {
                        panic!("pokedex replay error: CSR {name} = {value:#010x}")
                    });
                }
                ResetVector { pc } => {
                    state.is_reset = true;
                    state.reset_vector = *pc;
                }
                Poweroff { .. } => {
                    state.is_poweroff = true;
                }

                Load { .. } | Store { .. } => {
                    // here only replay core events
                    // memory events are intentionally ignored
                }
            }
        });

        dr
    }
}
