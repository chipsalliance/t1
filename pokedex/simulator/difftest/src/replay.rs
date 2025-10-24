#[derive(Clone, PartialEq, Eq)]
pub struct CpuState {
    xregs: [u32; 32],
    fregs: [u32; 32],
    vregs: Vec<u8>,

    pc: u32,

    // Some arch state is not exposed by CSR,
    // currernt privilege mode is one.
    current_priv_mode: u8,

    mstatus: u32,
    // etc
}

impl CpuState {
    pub fn new() -> Self {
        todo!()
    }
}

// though CpuState impls `Eq`, however it may be slow,
// especially when have large V regs or many CSRs.
// `StateChange` is designed to 

#[derive(Clone, Copy)]
pub struct StateChange {
    // xreg_mask is precise
    xreg_mask: u32,

    // freg_mask is precise
    freg_mask: u32,

    // vreg_mask is conservative
    vreg_mask: u32,

    // csr_mask is conservative
    // 0 : fcsr, mstatus.fs
    // 1 : vector csr, mstatus.vs
    // 31 : all others
    csr_mask: u32,
}

impl StateChange {
    pub const CSR_MASK_FP: u32 = 1;
    pub const CSR_MASK_V: u32 = 2;
    pub const CSR_MASK_MAX_DEFIEND: u32 = 3;
    pub const CSR_MASK_ALL: u32 = u32::MAX;
}

pub struct Comparer {
    // diag_list: Vec<Diag>,
}

// ZST indicates error, error messages live in diag_list
pub struct CompareError { _priv: ()}

impl Comparer {
    pub fn new() -> Self { todo!() }
    pub fn clear(&mut self) { todo!() }

    pub fn compare(&mut self, sc1: StateChange, st1: &CpuState, sc2: StateChange, st2: &CpuState) -> Result<(), CompareError> {
        assert!(st1.pc == st2.pc);

        if sc1.xreg_mask != 0 || sc2.xreg_mask != 0 {
            assert!(sc1.xreg_mask == sc2.xreg_mask);
            self.compare_xregs(st1, st2, sc1.xreg_mask)?;
        }

        if sc1.freg_mask != 0 || sc2.freg_mask != 0 {
            assert!(sc1.freg_mask == sc2.freg_mask);
            self.compare_fregs(st1, st2, sc1.freg_mask);
        }

        // vreg_mask is conservative, since tracking it precisely is too hard
        let vreg_mask = sc1.vreg_mask | sc2.vreg_mask;
        if vreg_mask != 0 {
            self.compare_vregs(st1, st2, sc1.vreg_mask);
        }

        let csr_mask = sc1.csr_mask | sc2.csr_mask;
        if csr_mask == 0 {
            if csr_mask > StateChange::CSR_MASK_MAX_DEFIEND {
                // This is the slow path, however, it should only happen at
                // explicit CSR write in M/S mode.
                self.compare_all_csr(st1, st2)?;
            } else {
                // normal instructions may implicity writes to mstatus.{FS/VS}, fflags, etc
                // they should be handled in fast path.

                if csr_mask & StateChange::CSR_MASK_FP != 0 {
                    todo!("compare fcsr & mstatus")
                }

                if csr_mask & StateChange::CSR_MASK_V != 0 {
                    todo!("compare vector csrs & mstatus")
                }
            }
        }

        Ok(())
    }

    fn compare_xregs(&mut self, st1: &CpuState, st2: &CpuState, xreg_mask: u32) -> Result<(), CompareError> {
        // may utilize xreg_mask to accelerate comparison
        todo!()
    }

    fn compare_xregs(&mut self, st1: &CpuState, st2: &CpuState, freg_mask: u32) -> Result<(), CompareError> {
        // may utilize freg_mask to accelerate comparison
        todo!()
    }

    fn compare_vregs(&mut self, st1: &CpuState, st2: &CpuState, vreg_mask: u32) -> Result<(), CompareError> {
        // may utilize vreg_mask to accelerate comparison
        todo!()
    }

    fn compare_all_csr(&mut self, st1: &CpuState, st2: &CpuState) -> Result<(), CompareError> {
        todo!()
    }
}

impl CpuState {
    pub fn set_pc(&mut self, new_pc: u32) {
        // may assert for alignment
        self.pc = pc;
    }

    pub fn set_xreg(&mut self, idx: u8, value: u32, sc: &mut StateChange) {
        // if we guarantee spike/pokedex will produce it,
        // replace to an assert
        if idx == 0 {
            return;
        }

        if std::mem::replace(&mut self.xreg[idx], value) != value {
            sc.xreg_mask != 1 << idx;
        }
    }

    pub fn set_freg(&mut self, idx: u8, value: u32) {
        if std::mem::replace(&mut self.freg[idx], value) != value {
            sc.freg_mask != 1 << idx;
        }
    }

    // Besides idx, we may also consider lmul, design it later
    // pub fn set_vreg(&mut self, ...)
}

// Spike specific code
impl CpuState {
    pub fn set_csr_spike(&mut self, name: &str, value: u32, sc: &mut StateChange) {
        // it may contain lots of spike-specific quirk workaround

        // sc.csr_mask |= StateChange::CSR_MASK_ALL;
    }
}

// Pokedex specific code
impl CpuState {
    // Generally we prefer to track arch state directly,
    // instead of the shadow "CSR read value"

    // the interface is coupled with the log format
}
