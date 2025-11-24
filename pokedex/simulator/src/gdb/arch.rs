use std::num::NonZero;

use gdbstub::arch::RegId;

/// RISC-V Register identifier.
#[derive(Debug, Clone, Copy)]
#[non_exhaustive]
pub enum RiscvRegId<const XLEN: usize, const VLEN: usize> {
    /// General Purpose Register (x0-x31).
    X(u8),
    /// Floating Point Register (f0-f31).
    F(u8),
    /// Program Counter.
    Pc,
    /// Control and Status Register.
    Csr(u16),
    /// Privilege level.
    Priv,
    /// Vector Register (v0-v31)
    V(u8),
}

impl<const XLEN: usize, const VLEN: usize> RegId for RiscvRegId<XLEN, VLEN> {
    // We use register number defined in
    // https://github.com/bminor/binutils-gdb/blob/master/gdb/riscv-tdep.h
    fn from_raw_id(id: usize) -> Option<(Self, Option<NonZero<usize>>)> {
        let (id, size) = match id {
            0..=31 => (Self::X(id as u8), XLEN / 8),
            32 => (Self::Pc, XLEN / 8),
            33..=64 => (Self::F((id - 33) as u8), XLEN / 8),
            65..=4160 => (Self::Csr((id - 65) as u16), XLEN / 8),
            4161 => (Self::Priv, 1),
            4162..=4193 if VLEN != 0 => (Self::V((id - 4162) as u8), VLEN / 8),
            _ => return None,
        };

        Some((id, Some(NonZero::new(size)?)))
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Config {
    pub xlen: u32,
    pub flen: u32,
    pub vlen: u32,
}

impl Config {
    pub fn build_target_xml(&self) -> String {
        let mut buf = String::with_capacity(4096);
        print_target_xml(&mut buf, *self).expect("format target to xml failed");
        buf
    }
}

fn print_target_xml(f: &mut dyn std::fmt::Write, config: Config) -> std::fmt::Result {
    let Config { xlen, flen, vlen } = config;

    let arch = match xlen {
        32 => "riscv:rv32",
        64 => "riscv:rv64",
        _ => unreachable!("unknown xlen={xlen}"),
    };

    writeln!(f, r#"<?xml version="1.0"?>"#)?;
    writeln!(f, r#"<!DOCTYPE target SYSTEM "gdb-target.dtd">"#)?;
    writeln!(f, r#"<target version="1.0">"#)?;

    writeln!(f, r#"  <architecture>{arch}</architecture>"#)?;

    print_target_xml_cpu_features(f, xlen)?;
    if flen != 0 {
        print_target_xml_fpu_features(f, flen)?;
    }
    print_target_xml_csr_features(f, config)?;
    if vlen != 0 {
        print_target_xml_vector_features(f, vlen)?;
    }

    writeln!(f, r#"</target>"#)
}

fn print_target_xml_cpu_features(f: &mut dyn std::fmt::Write, xlen: u32) -> std::fmt::Result {
    print_feature_begin(f, "org.gnu.gdb.riscv.cpu")?;

    for idx in 0..32 {
        let (name, ty) = XREGS_NAMES_TYPES[idx as usize];
        let reg = Reg {
            name,
            bitsize: xlen,
            ty,
            regnum: idx as u32,
            group: None,
        };
        print_reg(f, reg)?
    }

    let reg_pc = Reg {
        name: "pc",
        bitsize: xlen,
        ty: "code_ptr",
        regnum: 32,
        group: None,
    };
    print_reg(f, reg_pc)?;

    print_feature_end(f)
}

fn print_target_xml_fpu_features(f: &mut dyn std::fmt::Write, flen: u32) -> std::fmt::Result {
    print_feature_begin(f, "org.gnu.gdb.riscv.fpu")?;

    if flen == 64 {
        writeln!(f, r#"    <union id="riscv_double">"#)?;
        writeln!(f, r#"      <field name="float" type="ieee_single"/>"#)?;
        writeln!(f, r#"      <field name="double" type="ieee_double"/>"#)?;
        writeln!(f, r#"    </union>"#)?;
    }

    let ty = match flen {
        32 => "ieee_single",
        64 => "riscv_double",
        _ => unreachable!("unknown flen={flen}"),
    };

    for idx in 0..32 {
        let reg = Reg {
            name: FREG_NAMES[idx as usize],
            bitsize: flen,
            ty,
            regnum: 33 + (idx as u32),
            group: None,
        };
        print_reg(f, reg)?
    }

    print_feature_end(f)
}

fn print_target_xml_vector_features(f: &mut dyn std::fmt::Write, vlen: u32) -> std::fmt::Result {
    print_feature_begin(f, "org.gnu.gdb.riscv.vector")?;

    // writeln!(f, r#"    <vector id="quads" type="uint128" count="{}"/>"#, vlen / 128)?;
    // writeln!(f, r#"    <vector id="longs" type="uint64" count="{}"/>"#, vlen / 64)?;
    writeln!(
        f,
        r#"    <vector id="words" type="uint32" count="{}"/>"#,
        vlen / 32
    )?;
    writeln!(
        f,
        r#"    <vector id="shorts" type="uint16" count="{}"/>"#,
        vlen / 16
    )?;
    writeln!(
        f,
        r#"    <vector id="bytes" type="uint8" count="{}"/>"#,
        vlen / 8
    )?;
    writeln!(f, r#"    <union id="riscv_vector">"#)?;
    // writeln!(f, r#"      <field name="q" type="quads"/>"#)?;
    // writeln!(f, r#"      <field name="l" type="longs"/>"#)?;
    writeln!(f, r#"      <field name="w" type="words"/>"#)?;
    writeln!(f, r#"      <field name="s" type="shorts"/>"#)?;
    writeln!(f, r#"      <field name="b" type="bytes"/>"#)?;
    writeln!(f, r#"    </union>"#)?;

    for idx in 0..32 {
        let name = &format!("v{idx}");
        let reg = Reg {
            name,
            bitsize: vlen,
            ty: "riscv_vector",
            regnum: 4162 + (idx as u32),
            group: Some("vector"),
        };
        print_reg(f, reg)?
    }

    print_feature_end(f)
}

fn print_target_xml_csr_features(f: &mut dyn std::fmt::Write, config: Config) -> std::fmt::Result {
    let Config { xlen, flen, vlen } = config;

    fn csr(idx: u16, name: &str, xlen: u32) -> Reg<'_> {
        Reg {
            name,
            bitsize: xlen,
            ty: "int",
            regnum: 65 + (idx as u32),
            group: None,
        }
    }

    print_feature_begin(f, "org.gnu.gdb.riscv.csr")?;
    if flen != 0 {
        print_reg(f, csr(0x001, "fflags", xlen))?;
        print_reg(f, csr(0x002, "frm", xlen))?;
        print_reg(f, csr(0x003, "fcsr", xlen))?
    }
    if vlen != 0 {
        print_reg(f, csr(0x008, "vstart", xlen))?;
        print_reg(f, csr(0x009, "vxsat", xlen))?;
        print_reg(f, csr(0x00A, "vxrm", xlen))?;
        print_reg(f, csr(0x00F, "vcsr", xlen))?;
        print_reg(f, csr(0xC20, "vl", xlen))?;
        print_reg(f, csr(0xC21, "vtype", xlen))?;
        print_reg(f, csr(0xC22, "vlenb", xlen))?;
    }
    print_feature_end(f)?;
    Ok(())
}

fn print_feature_begin(f: &mut dyn std::fmt::Write, feature: &str) -> std::fmt::Result {
    writeln!(f, r#"  <feature name="{feature}">"#)
}

fn print_feature_end(f: &mut dyn std::fmt::Write) -> std::fmt::Result {
    writeln!(f, r#"  </feature>"#)
}

struct Reg<'a> {
    name: &'a str,
    bitsize: u32,
    ty: &'a str,
    regnum: u32,
    group: Option<&'a str>,
}

fn print_reg(f: &mut dyn std::fmt::Write, reg: Reg) -> std::fmt::Result {
    let Reg {
        name,
        bitsize,
        ty,
        regnum,
        group,
    } = reg;

    write!(f, "    <reg")?;

    write!(f, r#" name="{name}""#)?;
    write!(f, r#" bitsize="{bitsize}""#)?;
    write!(f, r#" type="{ty}""#)?;
    write!(f, r#" regnum="{regnum}""#)?;
    if let Some(group) = group {
        write!(f, r#" group="{group}""#)?;
    }

    writeln!(f, "/>")
}

const XREGS_NAMES_TYPES: &[(&str, &str); 32] = &[
    ("zero", "int"),
    ("ra", "code_ptr"),
    ("sp", "data_ptr"),
    ("gp", "data_ptr"),
    ("tp", "data_ptr"),
    ("t0", "int"),
    ("t1", "int"),
    ("t2", "int"),
    ("fp", "data_ptr"),
    ("s1", "int"),
    ("a0", "int"),
    ("a1", "int"),
    ("a2", "int"),
    ("a3", "int"),
    ("a4", "int"),
    ("a5", "int"),
    ("a6", "int"),
    ("a7", "int"),
    ("s2", "int"),
    ("s3", "int"),
    ("s4", "int"),
    ("s5", "int"),
    ("s6", "int"),
    ("s7", "int"),
    ("s8", "int"),
    ("s9", "int"),
    ("s10", "int"),
    ("s11", "int"),
    ("t3", "int"),
    ("t4", "int"),
    ("t5", "int"),
    ("t6", "int"),
];

#[rustfmt::skip]
const FREG_NAMES: &[&str; 32] = &[
    "ft0", "ft1", "ft2", "ft3",
    "ft4", "ft5", "ft6", "ft7",
    "fs0", "fs1", "fa0", "fa1",
    "fa2", "fa3", "fa4", "fa5",
    "fa6", "fa7", "fs2", "fs3",
    "fs4", "fs5", "fs6", "fs7",
    "fs8", "fs9", "fs10", "fs11",
    "ft8", "ft9", "ft10", "ft11",
];
