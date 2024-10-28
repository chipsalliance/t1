use std::{cell::OnceCell, collections::HashMap, ops::Range, rc::Rc};

use itertools::izip;
use vcd::IdCode;

use crate::disasm::Disasm;

pub enum SignalData {
    Scalar {
        data: Rc<Vec<ValueRecord<bool>>>,
    },
    Vector {
        width: u32,
        data: Rc<Vec<ValueRecord<u64>>>,
    },
}

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub struct ValueRecord<T: Copy> {
    pub cycle: u32,
    pub is_x: bool,
    pub value: T,
}

pub struct SVar {
    code: IdCode,
    name: String,
    record: OnceCell<Rc<Vec<ValueRecord<bool>>>>,
}

impl SVar {
    fn get_record(&self, cycle: u32) -> &ValueRecord<bool> {
        let records = self.records();
        let idx = records.partition_point(|c| c.cycle <= cycle);
        assert!(idx > 0, "cycle={cycle} is before FIRST_CYCLE");
        &records[idx - 1]
    }
    pub fn get_may_x(&self, cycle: u32) -> Option<bool> {
        let r = self.get_record(cycle);
        if r.is_x {
            None
        } else {
            Some(r.value)
        }
    }

    #[track_caller]
    pub fn get(&self, cycle: u32) -> bool {
        let r = self.get_record(cycle);
        assert!(!r.is_x, "signal '{}' is X at cycle={cycle}", self.name);
        r.value
    }
    pub fn records(&self) -> &[ValueRecord<bool>] {
        self.record.get().expect("record not set")
    }
    pub fn debug_print(&self) {
        match self.record.get() {
            None => println!("signal {} not set", self.code),
            Some(record) => {
                println!("signal {}:", self.code);
                for r in &record[..] {
                    let value = match (r.is_x, r.value) {
                        (true, _) => "x",
                        (false, true) => "1",
                        (false, false) => "0",
                    };
                    println!("- {} : {}", r.cycle, value);
                }
            }
        }
    }
}

pub struct VVar {
    code: IdCode,
    name: String,
    width: u32,
    records: OnceCell<Rc<Vec<ValueRecord<u64>>>>,
}

impl VVar {
    fn get_record(&self, cycle: u32) -> &ValueRecord<u64> {
        let records = self.records();
        let idx = records.partition_point(|c| c.cycle <= cycle);
        assert!(idx > 0, "cycle={cycle} is before FIRST_CYCLE");
        &records[idx - 1]
    }
    pub fn get(&self, cycle: u32) -> Option<u64> {
        let r = self.get_record(cycle);
        if r.is_x {
            None
        } else {
            Some(r.value)
        }
    }

    #[track_caller]
    pub fn get_u8(&self, cycle: u32) -> u8 {
        assert_eq!(self.width, 8);
        let r = self.get_record(cycle);
        assert!(!r.is_x, "signal '{}' is X at cycle={cycle}", self.name);
        r.value as u8
    }

    #[track_caller]
    pub fn get_u8_w(&self, cycle: u32, width: u32) -> u8 {
        assert_eq!(self.width, width);
        let r = self.get_record(cycle);
        assert!(!r.is_x, "signal '{}' is X at cycle={cycle}", self.name);
        r.value as u8
    }

    #[track_caller]
    pub fn get_option_u32(&self, cycle: u32) -> Option<u32> {
        assert_eq!(self.width, 32);
        let r = self.get_record(cycle);
        if !r.is_x {
            Some(r.value as u32)
        } else {
            None
        }
    }

    #[track_caller]
    pub fn get_u32(&self, cycle: u32) -> u32 {
        assert_eq!(self.width, 32);
        let r = self.get_record(cycle);
        assert!(!r.is_x, "signal '{}' is X at cycle={cycle}", self.name);
        r.value as u32
    }

    pub fn records(&self) -> &[ValueRecord<u64>] {
        self.records.get().expect("record not set")
    }
}

fn s(vars: &HashMap<String, (IdCode, Option<u32>)>, name: &str) -> SVar {
    let (code, width) = *vars
        .get(name)
        .unwrap_or_else(|| panic!("unable to find var '{name}'"));
    assert!(width.is_none());

    SVar {
        code,
        name: name.into(),
        record: OnceCell::new(),
    }
}

fn v(vars: &HashMap<String, (IdCode, Option<u32>)>, name: &str, width: u32) -> VVar {
    let (code, width_) = *vars
        .get(name)
        .unwrap_or_else(|| panic!("unable to find var '{name}'"));
    assert_eq!(width_, Some(width));

    VVar {
        code,
        name: name.into(),
        width,
        records: OnceCell::new(),
    }
}

fn ct<'a>(c: &mut VarCollector<'a>, s: &'a impl Collect) {
    Collect::collect_to(s, c);
}

#[derive(Default)]
pub struct VarCollector<'a> {
    vars: Vec<VarRef<'a>>,
}

impl<'a> VarCollector<'a> {
    pub fn new() -> Self {
        Self::default()
    }
    pub fn id_list(&self) -> Vec<(IdCode, Option<u32>)> {
        self.vars
            .iter()
            .map(|var| match var {
                VarRef::SVar(svar) => (svar.code, None),
                VarRef::VVar(vvar) => (vvar.code, Some(vvar.width)),
            })
            .collect()
    }
    pub fn set_with_signal_map(&self, signal_map: &HashMap<IdCode, SignalData>) {
        for &var in &self.vars {
            match var {
                VarRef::SVar(var) => match &signal_map[&var.code] {
                    SignalData::Scalar { data } => {
                        var.record
                            .set(data.clone())
                            .expect("signal record already set");
                    }
                    SignalData::Vector { .. } => unreachable!(),
                },
                VarRef::VVar(var) => match &signal_map[&var.code] {
                    SignalData::Vector { width, data } => {
                        assert_eq!(*width, var.width);
                        var.records
                            .set(data.clone())
                            .expect("signal record already set");
                    }
                    SignalData::Scalar { .. } => unreachable!(),
                },
            }
        }
    }
}

#[derive(Clone, Copy)]
enum VarRef<'a> {
    SVar(&'a SVar),
    VVar(&'a VVar),
}

pub trait Collect {
    fn collect_to<'a>(&'a self, c: &mut VarCollector<'a>);
}

impl Collect for SVar {
    fn collect_to<'a>(&'a self, c: &mut VarCollector<'a>) {
        c.vars.push(VarRef::SVar(self));
    }
}

impl Collect for VVar {
    fn collect_to<'a>(&'a self, c: &mut VarCollector<'a>) {
        c.vars.push(VarRef::VVar(self));
    }
}

pub struct InputVars {
    pub issue_enq: IssueEnq,
    pub issue_deq: IssueDeq,
    pub issue_req_deq: IssueRegDeq,
}

impl Collect for InputVars {
    fn collect_to<'a>(&'a self, c: &mut VarCollector<'a>) {
        ct(c, &self.issue_enq);
        ct(c, &self.issue_deq);
        ct(c, &self.issue_req_deq);
    }
}

pub struct IssueEnq {
    pub valid: SVar,
    pub ready: SVar,
    pub pc: VVar,
    pub inst: VVar,
    pub rs1: VVar,
    pub rs2: VVar,
    pub vtype: VVar,
    pub vl: VVar,
    pub vstart: VVar,
    pub vcsr: VVar,
}

impl Collect for IssueEnq {
    fn collect_to<'a>(&'a self, c: &mut VarCollector<'a>) {
        ct(c, &self.valid);
        ct(c, &self.ready);
        ct(c, &self.pc);
        ct(c, &self.inst);
        ct(c, &self.rs1);
        ct(c, &self.rs2);
        ct(c, &self.vtype);
        ct(c, &self.vl);
        ct(c, &self.vstart);
        ct(c, &self.vcsr);
    }
}

pub struct IssueDeq {
    pub valid: SVar,
    pub ready: SVar,
    pub inst: VVar,
    pub rs1: VVar,
    pub rs2: VVar,
    pub vtype: VVar,
    pub vl: VVar,
    pub vstart: VVar,
    pub vcsr: VVar,
}

impl Collect for IssueDeq {
    fn collect_to<'a>(&'a self, c: &mut VarCollector<'a>) {
        ct(c, &self.valid);
        ct(c, &self.ready);
        ct(c, &self.inst);
        ct(c, &self.rs1);
        ct(c, &self.rs2);
        ct(c, &self.vtype);
        ct(c, &self.vl);
        ct(c, &self.vstart);
        ct(c, &self.vcsr);
    }
}

pub struct IssueRegDeq {
    pub valid: SVar,
    pub ready: SVar,
    pub inst_idx: VVar,
    pub inst: VVar,
    pub rs1: VVar,
    pub rs2: VVar,
    pub vtype: VVar,
    pub vl: VVar,
    pub vstart: VVar,
    pub vcsr: VVar,
}

impl Collect for IssueRegDeq {
    fn collect_to<'a>(&'a self, c: &mut VarCollector<'a>) {
        ct(c, &self.valid);
        ct(c, &self.ready);
        ct(c, &self.inst_idx);
        ct(c, &self.inst);
        ct(c, &self.rs1);
        ct(c, &self.rs2);
        ct(c, &self.vtype);
        ct(c, &self.vl);
        ct(c, &self.vstart);
        ct(c, &self.vcsr);
    }
}

impl InputVars {
    pub fn from_vars(vars: &HashMap<String, (IdCode, Option<u32>)>) -> Self {
        InputVars {
            issue_enq: IssueEnq {
                valid: s(vars, "t1IssueEnq_valid"),
                ready: s(vars, "t1IssueEnq_ready"),
                pc: v(vars, "t1IssueEnqPc", 32),
                inst: v(vars, "t1IssueEnq_bits_instruction", 32),
                rs1: v(vars, "t1IssueEnq_bits_rs1Data", 32),
                rs2: v(vars, "t1IssueEnq_bits_rs2Data", 32),
                vtype: v(vars, "t1IssueEnq_bits_vtype", 32),
                vl: v(vars, "t1IssueEnq_bits_vl", 32),
                vstart: v(vars, "t1IssueEnq_bits_vstart", 32),
                vcsr: v(vars, "t1IssueEnq_bits_vcsr", 32),
            },
            issue_deq: IssueDeq {
                valid: s(vars, "t1IssueDeq_valid"),
                ready: s(vars, "t1IssueDeq_ready"),
                inst: v(vars, "t1IssueDeq_bits_instruction", 32),
                rs1: v(vars, "t1IssueDeq_bits_rs1Data", 32),
                rs2: v(vars, "t1IssueDeq_bits_rs2Data", 32),
                vtype: v(vars, "t1IssueDeq_bits_vtype", 32),
                vl: v(vars, "t1IssueDeq_bits_vl", 32),
                vstart: v(vars, "t1IssueDeq_bits_vstart", 32),
                vcsr: v(vars, "t1IssueDeq_bits_vcsr", 32),
            },
            issue_req_deq: IssueRegDeq {
                valid: s(vars, "t1IssueRegDeq_valid"),
                ready: s(vars, "t1IssueRegDeqReady"),
                inst_idx: v(vars, "t1IssueRegDeq_bits_instructionIndex", 3),
                inst: v(vars, "t1IssueRegDeq_bits_issue_instruction", 32),
                rs1: v(vars, "t1IssueRegDeq_bits_issue_rs1Data", 32),
                rs2: v(vars, "t1IssueRegDeq_bits_issue_rs2Data", 32),
                vtype: v(vars, "t1IssueRegDeq_bits_issue_vtype", 32),
                vl: v(vars, "t1IssueRegDeq_bits_issue_vl", 32),
                vstart: v(vars, "t1IssueRegDeq_bits_issue_vstart", 32),
                vcsr: v(vars, "t1IssueRegDeq_bits_issue_vcsr", 32),
            },
        }
    }

    pub fn collect(&self) -> VarCollector<'_> {
        let mut c = VarCollector::new();
        self.collect_to(&mut c);
        c
    }
}

pub trait CompatibleWith<U> {
    fn compatible_with(&self, u: &U);
}

pub fn compatible<T, U>(t: &T, u: &U)
where
    T: CompatibleWith<U>,
{
    t.compatible_with(u);
}

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub struct InstData {
    inst: u32,
    rs1: Option<u32>,
    rs2: Option<u32>,
    vtype: u32,
    vl: u32,
    vstart: u32,
    vcsr: u32,
}

impl InstData {
    pub fn csr_description(&self) -> String {
        assert!(self.vtype <= 0xFF);
        let sew = match (self.vtype >> 3) & 0x07 {
            0 => 8,
            1 => 16,
            2 => 32,
            3 => 64,
            _ => unreachable!(),
        };
        let lmul = match self.vtype & 0x07 {
            0 => "1",
            1 => "2",
            2 => "4",
            3 => "8",
            5 => "1/8",
            6 => "1/4",
            7 => "1/2",
            _ => unreachable!(),
        };
        let mut buf = format!("sew={sew:<2}, lmul={lmul}, vl={}", self.vl);

        if self.vstart != 0 {
            buf += &format!(", vstart={}", self.vstart);
        }

        buf
    }
}

pub struct IssueEnqData {
    cycle: u32,
    pc: u32,
    inst_data: InstData,
}

pub struct IssueDeqData {
    cycle: u32,
    inst_data: InstData,
}

pub struct IssueRegDeqData {
    cycle: u32,
    inst_idx: u8,
    inst_data: InstData,
}

impl CompatibleWith<IssueDeqData> for IssueEnqData {
    fn compatible_with(&self, u: &IssueDeqData) {
        let t = self;
        assert_eq!(t.inst_data, u.inst_data);
        assert!(t.cycle < u.cycle);
    }
}

impl CompatibleWith<IssueRegDeqData> for IssueDeqData {
    fn compatible_with(&self, u: &IssueRegDeqData) {
        let t = self;
        assert_eq!(t.inst_data, u.inst_data);
        assert!(t.cycle < u.cycle);
    }
}
pub struct IssueData {
    cycle_enq: u32,
    cycle_deq: u32,
    cycle_reg_deq: u32,
    inst_idx: u8,
    pc: u32,
    inst_data: InstData,
}

pub fn process(vars: &InputVars, range: Range<u32>) {
    let enq_data = process_issue_enq(&vars.issue_enq, range.clone());
    let deq_data = process_issue_deq(&vars.issue_deq, range.clone());
    let reg_deq_data = process_issue_reg_deq(&vars.issue_req_deq, range.clone());
    assert_eq!(enq_data.len(), deq_data.len());
    assert_eq!(enq_data.len(), reg_deq_data.len());

    // combine IssueEnq and IssueDeq
    let mut issue_data = vec![];
    for (enq, deq, reg_deq) in izip!(&enq_data, &deq_data, &reg_deq_data) {
        compatible(enq, deq);
        compatible(deq, reg_deq);

        issue_data.push(IssueData {
            cycle_enq: enq.cycle,
            cycle_deq: deq.cycle,
            cycle_reg_deq: reg_deq.cycle,
            inst_idx: reg_deq.inst_idx,
            pc: enq.pc,
            inst_data: enq.inst_data,
        })
    }

    let mut dis = Disasm::new();
    for (
        idx,
        &IssueData {
            cycle_enq,
            cycle_deq,
            cycle_reg_deq,
            inst_idx,
            pc,
            inst_data,
        },
    ) in issue_data.iter().enumerate()
    {
        let InstData { inst, rs1, rs2, .. } = inst_data;
        let diff_reg_deq_enq = cycle_reg_deq - cycle_enq;
        let disasm = dis.disasm(inst);
        let vcsr_desc = inst_data.csr_description();

        let mut q_len = 0;
        while q_len < idx && issue_data[idx - q_len].cycle_reg_deq > cycle_enq {
            q_len += 1;
        }
        let rs1 = rs1.unwrap_or(0xFFFFFFFF);
        let rs2 = rs2.unwrap_or(0xFFFFFFFF);
        println!("[PC=0x{pc:08x}, inst=0x{inst:08x}] {disasm:<24} |({inst_idx}) q_len={q_len:2}, enq={cycle_enq:5}, reg_deq-enq={diff_reg_deq_enq:4}, rs1 = 0x{rs1:08x}, rs2 = 0x{rs2:08x} | {vcsr_desc}");
    }
}

fn process_issue_enq(vars: &IssueEnq, range: Range<u32>) -> Vec<IssueEnqData> {
    let mut data = vec![];
    for cycle in range {
        match (vars.valid.get(cycle), vars.ready.get(cycle)) {
            (true, true) => {
                let pc = vars.pc.get_u32(cycle);
                let inst = vars.inst.get_u32(cycle);
                let rs1 = vars.rs1.get_option_u32(cycle);
                let rs2 = vars.rs2.get_option_u32(cycle);
                let vtype = vars.vtype.get_u32(cycle);
                let vl = vars.vl.get_u32(cycle);
                let vstart = vars.vstart.get_u32(cycle);
                let vcsr = vars.vcsr.get_u32(cycle);
                data.push(IssueEnqData {
                    cycle,
                    pc,
                    inst_data: InstData {
                        inst,
                        rs1,
                        rs2,
                        vtype,
                        vl,
                        vstart,
                        vcsr,
                    },
                });
            }
            (_, _) => {}
        }
    }
    data
}

fn process_issue_deq(vars: &IssueDeq, range: Range<u32>) -> Vec<IssueDeqData> {
    let mut data = vec![];
    for cycle in range {
        match (vars.valid.get(cycle), vars.ready.get(cycle)) {
            (true, true) => {
                let inst = vars.inst.get_u32(cycle);
                let rs1 = vars.rs1.get_option_u32(cycle);
                let rs2 = vars.rs2.get_option_u32(cycle);
                let vtype = vars.vtype.get_u32(cycle);
                let vl = vars.vl.get_u32(cycle);
                let vstart = vars.vstart.get_u32(cycle);
                let vcsr = vars.vcsr.get_u32(cycle);
                data.push(IssueDeqData {
                    cycle,
                    inst_data: InstData {
                        inst,
                        rs1,
                        rs2,
                        vtype,
                        vl,
                        vstart,
                        vcsr,
                    },
                });
            }
            (_, _) => {}
        }
    }
    data
}

fn process_issue_reg_deq(vars: &IssueRegDeq, range: Range<u32>) -> Vec<IssueRegDeqData> {
    let mut data = vec![];
    for cycle in range {
        match (vars.valid.get(cycle), vars.ready.get(cycle)) {
            (true, true) => {
                let inst_idx = vars.inst_idx.get_u8_w(cycle, 3);
                let inst = vars.inst.get_u32(cycle);
                let rs1 = vars.rs1.get_option_u32(cycle);
                let rs2 = vars.rs2.get_option_u32(cycle);
                let vtype = vars.vtype.get_u32(cycle);
                let vl = vars.vl.get_u32(cycle);
                let vstart = vars.vstart.get_u32(cycle);
                let vcsr = vars.vcsr.get_u32(cycle);
                data.push(IssueRegDeqData {
                    cycle,
                    inst_idx,
                    inst_data: InstData {
                        inst,
                        rs1,
                        rs2,
                        vtype,
                        vl,
                        vstart,
                        vcsr,
                    },
                });
            }
            (_, _) => {}
        }
    }
    data
}

mod op {
    pub fn and(op1: Option<bool>, op2: Option<bool>) -> Option<bool> {
        match (op1, op2) {
            (Some(true), Some(true)) => Some(true),
            (Some(false), _) => Some(false),
            (_, Some(false)) => Some(false),
            _ => None,
        }
    }
}
