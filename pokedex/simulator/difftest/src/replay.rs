use std::collections::HashMap;
use std::fmt::Display;
use std::iter::Peekable;
use std::slice::Iter;

#[derive(Clone, PartialEq, Eq)]
pub struct CpuState {
    pub(crate) gpr: [u32; 32],
    pub(crate) fpr: [u32; 32],
    pub(crate) vregs: Vec<u8>,

    pub(crate) pc: u32,

    pub(crate) csr: HashMap<u16, (String, u32)>,

    pub(crate) is_reset: bool,
    pub(crate) reset_vector: u32,
    pub(crate) is_poweroff: bool,
    // Some arch state is not exposed by CSR,
    // currernt privilege mode is one.
    // The ASL model supports only single core with M mode, so we are not going to test them right now
    // current_priv_mode: u8,
    // current_core: u8,
}

fn pretty_print_regs(
    f: &mut std::fmt::Formatter<'_>,
    prefix: &str,
    regs: &[u32],
) -> std::fmt::Result {
    assert_eq!(regs.len(), 32);

    const ROW_SIZE: usize = 4;
    const COLUMN_SIZE: usize = 8;

    for i in 0..ROW_SIZE {
        for j in 0..COLUMN_SIZE {
            let index = j + COLUMN_SIZE * i;
            let reg_val = regs[j + COLUMN_SIZE * i];
            write!(f, "{}{:<2}: {:#010x}  ", prefix, index, reg_val)?;
        }
        writeln!(f)?;
    }

    Ok(())
}

fn pretty_print_csr(
    f: &mut std::fmt::Formatter<'_>,
    csr: &HashMap<u16, (String, u32)>,
) -> std::fmt::Result {
    const COLUMN: usize = 4;

    let mut cursor = 0;
    for (id, (name, val)) in csr {
        write!(f, "({name} [{id}])={val:#010x}")?;
        cursor += 1;

        if cursor >= COLUMN {
            writeln!(f)?;
            cursor = 0;
        } else {
            write!(f, "  ")?;
        }
    }
    writeln!(f)?;

    Ok(())
}

impl Display for CpuState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        writeln!(f, "{}", "=".repeat(80))?;

        writeln!(f, "General Purpose Register dump at PC {:#010x}:", self.pc)?;
        pretty_print_regs(f, "x", &self.gpr)?;
        writeln!(f, "{}", "-".repeat(80))?;

        writeln!(f, "Floating Point Register dump at PC {:#010x}:", self.pc)?;
        pretty_print_regs(f, "f", &self.fpr)?;
        writeln!(f, "{}", "-".repeat(80))?;

        writeln!(f, "CSR dump at PC {:#010x}:", self.pc)?;
        pretty_print_csr(f, &self.csr)?;
        writeln!(f, "{}", "-".repeat(80))?;

        Ok(())
    }
}

impl CpuState {
    /// Return an unintiallze CpuState. Reset and emulator alignment should be handled on software side.
    pub fn new() -> Self {
        Self {
            gpr: [0; 32],
            fpr: [0; 32],
            vregs: Vec::new(),
            pc: 0,
            csr: HashMap::new(),
            reset_vector: 0,
            is_reset: false,
            is_poweroff: false,
        }
    }

    /// Update shadow GPR, return old data if the written data is different with it.
    pub(crate) fn write_gpr(&mut self, rd: usize, val: u32) -> Option<u32> {
        assert!(rd > 0 && rd < 32);
        let old_val = self.gpr[rd];
        if old_val != val {
            self.gpr[rd] = val;
            Some(old_val)
        } else {
            None
        }
    }

    /// Update shadow FPR, return old data if the written data is different with it.
    pub(crate) fn write_fpr(&mut self, rd: usize, val: u32) -> Option<u32> {
        assert!(rd < 32);
        let old_val = self.fpr[rd];
        if old_val != val {
            self.fpr[rd] = val;
            Some(old_val)
        } else {
            None
        }
    }

    pub(crate) fn write_csr(&mut self, name: &str, id: u16, val: u32) -> Option<u32> {
        let (_, entry) = self.csr.entry(id).or_insert((name.to_string(), val));
        let old_val = *entry;
        if old_val != val {
            *entry = val;
            Some(old_val)
        } else {
            None
        }
    }

    pub(crate) fn write_vreg(&mut self, data: &[u8], mask: &[bool]) -> Option<Vec<u8>> {
        todo!()
    }
}

/// CSR Write is conservative, it can be an implicit write from instruction or an explicit write
/// request by Zicsr instructions. Distinguish those writes could speed up diff test.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CsrCheckType {
    NoWrite,
    AllCsr,
    FpCsrOnly,
    VecCsrOnly,
}

impl Default for CsrCheckType {
    fn default() -> Self {
        Self::NoWrite
    }
}

impl CsrCheckType {
    pub(crate) fn has_write(&self) -> bool {
        !(matches!(self, Self::NoWrite))
    }
}

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub struct StateCheckType {
    // rd for general propose register
    pub(crate) gpr_rd: Option<usize>,
    // rd for floating point register
    pub(crate) fpr_rd: Option<usize>,
    // written data index mask for the concatenated vector register
    pub(crate) vreg_mask: Option<u32>,
    // possible write type for CSR
    pub(crate) csr_mask: CsrCheckType,
}

pub trait IsInsnCommit {
    fn get_pc(&self) -> u32;
    fn write_cpu_state(&self, state: &mut CpuState) -> StateCheckType;
}

pub struct CommitCassette<'a, 'b, T>
where
    T: IsInsnCommit,
{
    commit_cursor: &'a mut Peekable<Iter<'b, T>>,
    state: CpuState,
}

impl<'a, 'b, T> CommitCassette<'a, 'b, T>
where
    T: IsInsnCommit + std::fmt::Debug,
{
    pub fn new(commit_cursor: &'a mut Peekable<Iter<'b, T>>) -> Self {
        Self {
            state: CpuState::new(),
            commit_cursor,
        }
    }

    /// Roll the commit until PC match, return true if current commit indeed has given PC
    pub fn roll_until(&mut self, pc: u32) -> bool {
        for commit in self.commit_cursor.by_ref() {
            if commit.get_pc() == pc {
                return true;
            }
        }

        false
    }

    pub fn roll(&mut self) -> Option<StateCheckType> {
        let check_ty = self
            .commit_cursor
            .peek()
            .map(|commit| commit.write_cpu_state(&mut self.state));
        let _ = self.commit_cursor.next();
        check_ty
    }

    pub fn get_state(&self) -> &CpuState {
        &self.state
    }
}
