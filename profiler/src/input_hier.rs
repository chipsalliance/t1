use std::{cell::OnceCell, collections::HashMap, rc::Rc};

use vcd::IdCode;

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
    record: OnceCell<Rc<Vec<ValueRecord<bool>>>>,
}

impl SVar {
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
    width: u32,
    record: OnceCell<Rc<Vec<ValueRecord<u64>>>>,
}

fn s(vars: &HashMap<String, (IdCode, Option<u32>)>, name: &str) -> SVar {
    let (code, width) = *vars
        .get(name)
        .unwrap_or_else(|| panic!("unable to find var '{name}'"));
    assert!(width.is_none());

    SVar {
        code,
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
        width,
        record: OnceCell::new(),
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
                        var.record
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
}

impl Collect for InputVars {
    fn collect_to<'a>(&'a self, c: &mut VarCollector<'a>) {
        ct(c, &self.issue_enq);
    }
}

pub struct IssueEnq {
    pub valid: SVar,
    pub ready: SVar,
    pub pc: VVar,
    pub inst: VVar,
    pub rs1: VVar,
    pub rs2: VVar,
}

impl Collect for IssueEnq {
    fn collect_to<'a>(&'a self, c: &mut VarCollector<'a>) {
        ct(c, &self.valid);
        ct(c, &self.ready);
        ct(c, &self.pc);
        ct(c, &self.inst);
        ct(c, &self.rs1);
        ct(c, &self.rs2);
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
            },
        }
    }

    pub fn collect(&self) -> VarCollector<'_> {
        let mut c = VarCollector::new();
        self.collect_to(&mut c);
        c
    }
}
