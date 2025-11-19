use std::path::Path;

use anyhow::Context as _;

use crate::{
    common::{CommitLog, PokedexLog},
    difftest::{
        DiffBackend, Status,
        replay::{CpuState, DiffRecord},
    },
};

pub fn backend_from_log(log_path: &Path) -> anyhow::Result<PokedexLogBackend> {
    // FIXME: parse in stream

    let raw_str = std::fs::read_to_string(log_path)
        .with_context(|| format!("reading pokedex log {}", log_path.display()))?;

    let mut pokedex_log = vec![];
    for (line_number, line_str) in raw_str.lines().enumerate() {
        let commit: PokedexLog = serde_json::from_str(line_str).with_context(|| {
            format!(
                "fail parse pokedex log {}, line {line_number}",
                log_path.display(),
            )
        })?;
        pokedex_log.push(commit);
    }

    Ok(PokedexLogBackend::new(pokedex_log))
}

pub struct PokedexLogBackend {
    index: usize,
    logs: Vec<PokedexLog>,

    state: CpuState,
}

impl PokedexLogBackend {
    pub fn new(logs: Vec<PokedexLog>) -> Self {
        Self {
            index: 0,
            logs,
            state: CpuState::new(),
        }
    }

    pub fn get_reset_pc(&self) -> u32 {
        match &self.logs[0] {
            &PokedexLog::Reset { pc } => pc,
            _ => panic!("pokede json log should start with reset"),
        }
    }
}

impl DiffBackend for PokedexLogBackend {
    fn description(&self) -> String {
        "pokedex-log".into()
    }

    fn diff_reset(&mut self, expected_pc: u32) -> anyhow::Result<()> {
        match &self.logs[self.index] {
            &PokedexLog::Reset { pc } => {
                if pc != expected_pc {
                    anyhow::bail!(
                        "pokedex error at reset, expected_pc={expected_pc:#010x}, actual_pc={pc:#010x}"
                    );
                }
            }
            _ => anyhow::bail!("pokedex json log should start with reset"),
        }

        self.index += 1;
        Ok(())
    }

    fn diff_step(&mut self) -> anyhow::Result<Status> {
        match self.logs.get(self.index) {
            Some(&PokedexLog::Exit { code }) => Ok(Status::Exit { code }),
            Some(PokedexLog::Commit(commit)) => {
                let dr = update_state(&mut self.state, commit);
                self.index += 1;
                Ok(Status::Running(dr))
            }

            None => anyhow::bail!("pokedex json log should end with exit"),
            Some(x) => anyhow::bail!(" unexpected log {x:?}"),
        }
    }

    fn state(&self) -> &CpuState {
        &self.state
    }
}

fn update_state(state: &mut CpuState, commit: &CommitLog) -> DiffRecord {
    state.pc = commit.pc;

    let mut dr = DiffRecord::default();
    for write in &commit.states_changed {
        use crate::common::StateWrite::*;

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
                state
                    .write_csr(name, value)
                    .unwrap_or_else(|_| panic!("pokedex replay error: CSR {name} = {value:#010x}"));
            }
        }
    }

    dr
}
