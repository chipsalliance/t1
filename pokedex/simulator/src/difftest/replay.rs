use crate::util::{self, Bitmap32};

const VLEN: usize = 256;
const VLEN_BYTE: usize = VLEN / 8;

#[derive(Clone, PartialEq, Eq)]
pub struct CpuState {
    pub gpr: [u32; 32],
    pub fpr: [u32; 32],
    pub vregs: Vec<u8>,

    pub pc: u32,

    pub csr: CsrState,

    pub(crate) is_reset: bool,
    pub(crate) reset_vector: u32,
    pub(crate) is_poweroff: bool,
    // Some arch state is not exposed by CSR,
    // currernt privilege mode is one.
    // The ASL model supports only single core with M mode, so we are not going to test them right now
    // current_priv_mode: u8,
    // current_core: u8,
}

#[derive(Clone, PartialEq, Eq)]
pub struct CsrState {
    pub fcsr: u32,
    pub vtype: u32,
    pub vl: u32,
    pub vcsr: u32,
    pub vstart: u32,
    pub mstatus: u32,
    pub mstatush: u32,
    pub mtvec: u32,
    pub mtval: u32,
    pub mepc: u32,
    pub mie: u32,
    pub mscratch: u32,
}

impl Default for CsrState {
    fn default() -> Self {
        Self {
            fcsr: 0,
            vtype: 0x80000000, // vtype.ill is set
            vl: 0,
            vcsr: 0,
            vstart: 0,
            mstatus: 0x00001800, // mstatus.mpp = M
            mstatush: 0,
            mtvec: 0,
            mtval: 0,
            mepc: 0,
            mie: 0,
            mscratch: 0,
        }
    }
}

// Record which part of CpuState is modified.
// The record is conservative, which means if the mask is spurious set,
// the comparison may be slower but still correct.
#[derive(Debug, Clone, Default)]
pub struct DiffRecord {
    gpr_write_mask: Bitmap32,
    fpr_write_mask: Bitmap32,
    vreg_write_mask: Bitmap32,
}

impl DiffRecord {
    pub fn compare(&self, x: &CpuState, y: &CpuState) -> bool {
        x.pc == y.pc
            && self.compare_gpr(x, y)
            && self.compare_fpr(x, y)
            && self.compare_vreg(x, y)
            && Self::compare_csr_all(x, y)
    }

    pub fn combine(lhs: &DiffRecord, rhs: &DiffRecord) -> DiffRecord {
        DiffRecord {
            gpr_write_mask: lhs.gpr_write_mask | rhs.gpr_write_mask,
            fpr_write_mask: lhs.fpr_write_mask | rhs.fpr_write_mask,
            vreg_write_mask: lhs.vreg_write_mask | rhs.vreg_write_mask,
        }
    }

    fn compare_gpr(&self, x: &CpuState, y: &CpuState) -> bool {
        self.gpr_write_mask.indices().all(|i| x.gpr[i] == y.gpr[i])
    }

    fn compare_fpr(&self, x: &CpuState, y: &CpuState) -> bool {
        self.fpr_write_mask.indices().all(|i| x.fpr[i] == y.fpr[i])
    }

    fn compare_vreg(&self, x: &CpuState, y: &CpuState) -> bool {
        self.vreg_write_mask
            .indices()
            .all(|i| x.vreg_slice(i) == y.vreg_slice(i))
    }

    fn compare_csr_all(x: &CpuState, y: &CpuState) -> bool {
        x.csr == y.csr
    }
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

fn pretty_print_csr(f: &mut std::fmt::Formatter<'_>, csr: &CsrState) -> std::fmt::Result {
    const COLUMN: usize = 4;

    // FIXME: find a better way than hard-coding
    let csr = [
        ("fcsr", csr.fcsr),
        ("vtype", csr.vtype),
        ("vl", csr.vl),
        ("vcsr", csr.vcsr),
        ("vstart", csr.vstart),
        ("mstatus", csr.mstatus),
        ("mstatush", csr.mstatush),
        ("mtvec", csr.mtvec),
        ("mtval", csr.mtval),
        ("mepc", csr.mepc),
        ("mie", csr.mie),
        ("mscratch", csr.mscratch),
    ];

    let mut cursor = 0;
    for (name, val) in csr {
        write!(f, "{name:<8} = {val:#010x}")?;
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

pub fn pretty_print_diff(
    f: &mut std::fmt::Formatter<'_>,
    gold: &CpuState,
    dut: &CpuState,
) -> std::fmt::Result {
    if gold.pc != dut.pc {
        writeln!(f, "pc        : {:010x} <-> {:010x}", gold.pc, dut.pc)?;
    } else {
        writeln!(f, "pc        : {:010x}", gold.pc)?;
    }

    // compare GPR
    for i in 0..32 {
        let (goldv, dutv) = (gold.gpr[i], dut.gpr[i]);
        if goldv != dutv {
            writeln!(f, "x{i:<9} : {goldv:#010x} <-> {dutv:#010x}")?;
        }
    }

    // compare FPR
    for i in 0..32 {
        let (goldv, dutv) = (gold.fpr[i], dut.fpr[i]);
        if goldv != dutv {
            writeln!(f, "f{i:<9} : {goldv:#010x} <-> {dutv:#010x}")?;
        }
    }

    macro_rules! diff_csr {
        ($csr: ident) => {
            let goldv = gold.csr.$csr;
            let dutv = dut.csr.$csr;
            if goldv != dutv {
                writeln!(
                    f,
                    "{:<10} : {goldv:#010x} <-> {dutv:#010x}",
                    stringify!($csr)
                )?;
            }
        };
    }

    // FIXME: find a better way than hard-coding
    diff_csr!(fcsr);
    diff_csr!(vtype);
    diff_csr!(vl);
    diff_csr!(vcsr);
    diff_csr!(vstart);
    diff_csr!(mstatus);
    diff_csr!(mstatush);
    diff_csr!(mtvec);
    diff_csr!(mtval);
    diff_csr!(mepc);
    diff_csr!(mie);
    diff_csr!(mscratch);

    // compare vector regs
    if gold.vregs != dut.vregs {
        writeln!(f, "note: vreg diff is organized in 8-byte fragments")?;
        writeln!(
            f,
            "      frags are shown as hex bytes, the left is the least byte"
        )?;
        for i in 0..32 {
            let goldv = gold.vreg_slice(i);
            let dutv = dut.vreg_slice(i);
            if goldv != dutv {
                for j in 0..VLEN / 64 {
                    let goldv_frag = &goldv[8 * j..][..8];
                    let dutv_frag = &dutv[8 * j..][..8];
                    writeln!(
                        f,
                        "v{i:<2} [{:4} +: 64] : {goldv_frag:02x?} <-> {dutv_frag:02x?}",
                        j * 64
                    )?;
                }
            }
        }
    }

    Ok(())
}

impl CpuState {
    pub fn pretty_print(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
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

    pub fn pretty_print_string(&self) -> String {
        util::fn_to_string(|f| self.pretty_print(f))
    }
}

impl CpuState {
    /// Return an unintiallze CpuState. Reset and emulator alignment should be handled on software side.
    pub fn new() -> Self {
        Self {
            gpr: [0; 32],
            fpr: [0; 32],
            vregs: vec![0; 32 * VLEN_BYTE],
            pc: 0,

            csr: CsrState::default(),

            reset_vector: 0,
            is_reset: false,
            is_poweroff: false,
        }
    }

    pub(crate) fn write_gpr(&mut self, rd: usize, val: u32, diff: &mut DiffRecord) {
        assert!(rd > 0 && rd < 32);
        self.gpr[rd] = val;
        diff.gpr_write_mask.set(rd);
    }

    pub(crate) fn write_fpr(&mut self, rd: usize, val: u32, diff: &mut DiffRecord) {
        assert!(rd < 32);
        self.fpr[rd] = val;
        diff.fpr_write_mask.set(rd);
    }

    pub(crate) fn write_vreg(&mut self, rd: usize, data: &[u8], diff: &mut DiffRecord) {
        assert!(rd < 32);
        assert_eq!(data.len(), VLEN_BYTE);
        self.vreg_slice_mut(rd).copy_from_slice(data);
        diff.vreg_write_mask.set(rd);
    }

    fn vreg_slice(&self, idx: usize) -> &[u8] {
        &self.vregs[idx * VLEN_BYTE..][..VLEN_BYTE]
    }

    fn vreg_slice_mut(&mut self, idx: usize) -> &mut [u8] {
        &mut self.vregs[idx * VLEN_BYTE..][..VLEN_BYTE]
    }

    pub(crate) fn write_csr(&mut self, name: &str, val: u32) -> Result<(), CsrValueError> {
        const MASK_FCSR: u32 = 0xFF;
        const MASK_FFLAGS: u32 = 0x1F;
        const MASK_FRM: u32 = 0x07;
        const MASK_VCSR: u32 = 0x07;
        const MASK_VXRM: u32 = 0x03;
        const MASK_VXSAT: u32 = 0x01;

        macro_rules! ensure {
            ($cond: expr) => {
                if !($cond) {
                    return Err(CsrValueError);
                }
            };
        }

        fn update(src: &mut u32, value: u32, mask: u32) {
            *src = (*src & !mask) | (value & mask);
        }

        match name {
            "fcsr" => {
                ensure!(val == val & MASK_FCSR);
                self.csr.fcsr = val;
            }
            "fflags" => {
                ensure!(val == val & MASK_FFLAGS);
                update(&mut self.csr.fcsr, val, MASK_FFLAGS);
            }
            "frm" => {
                ensure!(val == val & MASK_FRM);
                update(&mut self.csr.fcsr, val << 5, MASK_FRM << 5);
            }

            "vtype" => {
                // ensure!(...);
                self.csr.vtype = val;
            }
            "vl" => {
                // ensure!(val <= VLEN);
                self.csr.vl = val;
            }
            "vcsr" => {
                ensure!(val == val & MASK_VCSR);
                self.csr.vcsr = val;
            }
            "vxrm" => {
                ensure!(val == val & MASK_VXRM);
                update(&mut self.csr.vcsr, val << 1, MASK_VXRM << 1);
            }
            "vxsat" => {
                ensure!(val == val & MASK_VXSAT);
                update(&mut self.csr.vcsr, val, MASK_VXSAT);
            }
            "vstart" => {
                // ensure!(val <= VLEN);
                self.csr.vstart = val;
            }

            "mstatus" => {
                // check_mstatus(val)?;
                self.csr.mstatus = val;
            }
            "mstatush" => {
                // check_mstatush(val)?;
                self.csr.mstatush = val;
            }
            "mtvec" => {
                // check_mtvec(val)?;
                self.csr.mtvec = val;
            }
            "mepc" => {
                // check_mepc(val)?;
                self.csr.mepc = val;
            }
            "mie" => {
                // check_mie(val)?;
                self.csr.mie = val;
            }
            "mscratch" => {
                self.csr.mscratch = val;
            }

            _ => return Err(CsrValueError),
        }

        Ok(())
    }
}

pub struct CsrValueError;
