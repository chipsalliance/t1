use std::fmt::Display;

// TODO: share library
#[derive(Debug, PartialEq, Eq, serde::Deserialize)]
#[serde(tag = "dest", rename_all = "lowercase")]
pub(crate) enum ModelStateWrite {
    Xrf { rd: u8, value: u32 },
    Frf { rd: u8, value: u32 },
    Csr { idx: u16, name: String, value: u32 },
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
            Csr { idx, name, value } => write!(f, "[csr {idx} {name} <- {value:#010x}]"),
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

impl crate::replay::IsInsnCommit for InsnCommit {
    fn get_pc(&self) -> u32 {
        self.pc as u32
    }

    fn write_cpu_state(
        &self,
        state: &mut crate::replay::CpuState,
    ) -> crate::replay::StateCheckType {
        state.pc = self.pc as u32;

        let mut check_ty = crate::replay::StateCheckType::default();

        let mut has_frf_write = false;
        let mut has_csr_write = false;
        self.states_changed.iter().for_each(|write| {
            use crate::replay::CsrCheckType;
            use ModelStateWrite::*;

            match write {
                Xrf { rd, value } => {
                    if state.write_gpr((*rd) as usize, *value).is_some() {
                        check_ty.gpr_rd = Some((*rd) as usize);
                    }
                }
                Frf { rd, value } => {
                    if state.write_fpr((*rd) as usize, *value).is_some() {
                        check_ty.fpr_rd = Some((*rd) as usize);
                    }
                    has_frf_write = true;
                }
                Csr { idx, name, value } => {
                    if state.write_csr(name, *idx, *value).is_some() {
                        check_ty.csr_mask = CsrCheckType::AllCsr;
                    }
                    has_csr_write = true;
                }
                ResetVector { pc } => {
                    state.is_reset = true;
                    state.reset_vector = *pc;
                }
                Poweroff { .. } => {
                    state.is_poweroff = true;
                }
                _ => (),
            }

            if has_frf_write && has_csr_write {
                check_ty.csr_mask = CsrCheckType::FpCsrOnly;
            }
        });

        check_ty
    }
}
