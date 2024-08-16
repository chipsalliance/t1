use std::collections::HashMap;
use tracing::trace;
use Default;

use crate::clip;
use crate::Spike;

#[derive(Debug, Clone)]
pub struct SingleMemWrite {
  pub val: u8,
  pub executed: bool, // set to true when rtl execute this mem access
}

#[derive(Debug, Clone)]
pub struct SingleMemRead {
  pub val: u8,
  pub executed: bool, // set to true when rtl execute this mem access
}

#[derive(Debug, Clone)]
pub struct MemWriteRecord {
  pub writes: Vec<SingleMemWrite>,
  pub num_completed_writes: usize,
}

#[derive(Debug, Clone)]
pub struct MemReadRecord {
  pub reads: Vec<SingleMemRead>,
  pub num_completed_reads: usize,
}

#[derive(Debug, Clone)]
pub struct SingleVrfWrite {
  pub byte: u8,
  pub executed: bool, // set to true when rtl execute this mem access
}

#[derive(Default, Debug, Clone)]
pub struct VdWriteRecord {
  vd_bytes: Vec<u8>,
}

#[derive(Default, Debug, Clone)]
pub struct MemAccessRecord {
  pub all_writes: HashMap<u32, MemWriteRecord>,
  pub all_reads: HashMap<u32, MemReadRecord>,
}

#[derive(Default, Debug, Clone)]
pub struct VrfAccessRecord {
  pub all_writes: HashMap<usize, SingleVrfWrite>,
  pub unretired_writes: Option<u32>,
  pub retired_writes: u32,
}

pub const LSU_IDX_DEFAULT: u8 = 0xff;
pub const ISSUE_IDX_DEFAULT: u8 = 0xff;

#[derive(Default, Debug, Clone)]
pub struct SpikeEvent {
  pub do_log_vrf: bool,

  // index
  pub lsu_idx: u8,
  pub issue_idx: u8,

  // instruction
  pub disasm: String,
  pub pc: u64,
  pub inst_bits: u32,

  // scalar to vector interface(used for driver)
  pub rs1: u32,
  pub rs2: u32,
  pub rs1_bits: u32,
  pub rs2_bits: u32,
  pub rd_idx: u32,

  // vtype
  pub vtype: u32,
  pub vxrm: u32,
  pub vnf: u32,

  // other CSR
  pub vill: bool,
  pub vxsat: bool,
  pub vl: u32,
  pub vstart: u16,

  // rd
  pub rd_bits: u32,

  // mutable states
  pub is_rd_written: bool,
  pub vd_write_record: VdWriteRecord,
  pub mem_access_record: MemAccessRecord,
  pub vrf_access_record: VrfAccessRecord,

  pub exit: bool,
}

impl SpikeEvent {
  
  pub fn new_with_pc(pc: u64, do_log_vrf: bool) -> Self {
    SpikeEvent {
      do_log_vrf,
      
      lsu_idx: LSU_IDX_DEFAULT,
      issue_idx: ISSUE_IDX_DEFAULT,

      disasm: "".to_string(),
      pc: pc,
      inst_bits: 0, 

      rs1: 0,
      rs2: 0,
      rs1_bits: 0,
      rs2_bits: 0,
      rd_idx: 0,

      vtype: 0,
      vxrm: 0,
      vnf: 0,

      vill: false,
      vxsat: false,
      vl: 0,
      vstart: 0,

      rd_bits: 0,

      is_rd_written: false,
      vd_write_record: Default::default(),
      mem_access_record: Default::default(),
      vrf_access_record: Default::default(),

      exit: false,
    }
  }

  pub fn new(spike: &Spike, do_log_vrf: bool) -> Self {
    let proc = spike.get_proc();
    let state = proc.get_state();
    let inst_bits = proc.get_insn();

    let opcode = clip(inst_bits, 0, 6);
    let width = clip(inst_bits, 12, 14);

    let is_rs_fp = opcode == 0b1010111 && width == 0b101/* OPFVF */;
    // early return vsetvl scalar instruction

    // rs1, rs2
    let (rs1, rs2) = (proc.get_rs1(), proc.get_rs2());

    SpikeEvent {
      do_log_vrf,

      lsu_idx: LSU_IDX_DEFAULT,
      issue_idx: ISSUE_IDX_DEFAULT,

      disasm: spike.get_proc().disassemble(),
      pc: proc.get_state().get_pc(),
      inst_bits,

      rs1,
      rs2,
      rs1_bits: state.get_reg(rs1, is_rs_fp),
      rs2_bits: state.get_reg(rs2, is_rs_fp),
      rd_idx: proc.get_rd(),

      vtype: proc.vu_get_vtype(),
      vxrm: proc.vu_get_vxrm(),
      vnf: proc.vu_get_vnf(),

      vill: proc.vu_get_vill(),
      vxsat: proc.vu_get_vxsat(),
      vl: proc.vu_get_vl(),
      vstart: proc.vu_get_vstart(),

      rd_bits: Default::default(),

      is_rd_written: false,
      vd_write_record: Default::default(),
      mem_access_record: Default::default(),
      vrf_access_record: Default::default(),

      exit: false,
    }
  }

  pub fn opcode(&self) -> u32 {
    clip(self.inst_bits, 0, 6)
  }

  pub fn width(&self) -> u32 {
    clip(self.inst_bits, 12, 14)
  }

  pub fn rs1(&self) -> u32 {
    clip(self.inst_bits, 15, 19)
  }

  pub fn csr(&self) -> u32 {
    clip(self.inst_bits, 20, 31)
  }

  pub fn funct6(&self) -> u32 {
    clip(self.inst_bits, 26, 31)
  }

  pub fn mop(&self) -> u32 {
    clip(self.inst_bits, 26, 27)
  }

  pub fn lumop(&self) -> u32 {
    clip(self.inst_bits, 20, 24)
  }

  pub fn vm(&self) -> bool {
    clip(self.inst_bits, 25, 25) != 0
  }

  // check whether the instruction is a vector load
  pub fn is_vload(&self) -> bool {
    self.opcode() == 0b0000111 && self.width().wrapping_sub(1) & 0b100 != 0
  }

  // check whether the instruction is a vector store
  pub fn is_vstore(&self) -> bool {
    self.opcode() == 0b0100111 && self.width().wrapping_sub(1) & 0b100 != 0
  }

  pub fn is_v(&self) -> bool {
    (self.opcode() == 0b1010111 || self.is_vload() || self.is_vstore()) && !self.is_vsetvl()
  }

  pub fn is_vsetvl(&self) -> bool {
    self.opcode() == 0b1010111 && self.width() == 0b111
  }

  pub fn is_scalar(&self) -> bool {
    !self.is_v()
  }

  // check whether the instruction is a scalar load
  pub fn is_load(&self) -> bool {
    self.opcode() == 0b0000011 || self.is_cl()
  }

  // check whether the instruction is a scalar store
  pub fn is_store(&self) -> bool {
    self.opcode() == 0b0100011 || self.is_cw()
  }

  pub fn is_rd_written(&self) -> bool {
    self.is_rd_written
  }

  pub fn is_whole(&self) -> bool {
    self.mop() == 0 && self.lumop() == 8
  }

  pub fn is_widening(&self) -> bool {
    self.opcode() == 0b1010111 && (self.funct6() >> 4) == 0b11
  }

  pub fn is_mask_vd(&self) -> bool {
    self.opcode() == 0b1010111 && (self.funct6() >> 4) == 0b11
  }

  pub fn is_exit(&self) -> bool {
    self.exit
  }

  pub fn is_rd_fp(&self) -> bool {
    (self.opcode() == 0b1010111)
      && (self.rs1 == 0)
      && (self.funct6() == 0b010000)
      && self.vm()
      && (self.width() == 0b001)
  }

  pub fn c_op(&self) -> u32 {
    clip(self.inst_bits, 0, 1)
  }

  pub fn c_func3(&self) -> u32 {
    clip(self.inst_bits, 13, 15)
  }

  pub fn is_cl(&self) -> bool {
    ( self.c_op() == 0b00 && self.c_func3() & 0b100 == 0 ) || /* c.lw */
    ( self.c_op() == 0b10 && self.c_func3() & 0b100 == 0 ) /* c.lwsp */
  }

  pub fn is_cw(&self) -> bool {
    ( self.c_op() == 0b00 && self.c_func3() & 0b100 != 0 ) || /* c.sw */
    ( self.c_op() == 0b10 && self.c_func3() & 0b100 != 0 ) /* c.swsp */
  }

  pub fn vlmul(&self) -> u32 {
    clip(self.vtype, 0, 2)
  }

  pub fn vma(&self) -> bool {
    clip(self.vtype, 7, 7) != 0
  }

  pub fn vta(&self) -> bool {
    clip(self.vtype, 6, 6) != 0
  }

  pub fn vsew(&self) -> u32 {
    clip(self.vtype, 3, 5)
  }

  pub fn vcsr(&self) -> u32 {
    self.vxsat as u32 | self.vxrm << 1
  }

  pub fn describe_insn(&self) -> String {
    format!(
      "pc={:#x}, disasm='{}', bits={:#x}",
      self.pc as u32, self.disasm, self.inst_bits
    )
  }

  pub fn log_reg_write(&mut self, spike: &Spike) -> anyhow::Result<()> {
    let proc = spike.get_proc();
    let state = proc.get_state();
    // in spike, log_reg_write is arrange:
    // xx0000 <- x
    // xx0001 <- f
    // xx0010 <- vreg
    // xx0011 <- vec
    // xx0100 <- csr
    let reg_write_size = state.get_reg_write_size();
    // TODO: refactor it.
    (0..reg_write_size).for_each(|idx| match state.get_reg_write_index(idx) & 0xf {
      0b0000 => {
        // scalar rf
        let data = state.get_reg(self.rd_idx, false);
        self.is_rd_written = true;
        self.rd_bits = data;
        trace!("ScalarRFChange: idx={:02x}, data={:08x}", self.rd_idx, self.rd_bits);
      }
      0b0001 => {
        let data = state.get_reg(self.rd_idx, true);
        self.is_rd_written = true;
        self.rd_bits = data;
        trace!("FloatRFChange: idx={:02x}, data={:08x}", self.rd_idx, self.rd_bits);
      }
      _ => trace!(
        "UnknownRegChange, idx={:02x}, spike detect unknown reg change",
        state.get_reg_write_index(idx)
      ),
    });

    Ok(())
  }

  pub fn log_mem_write(&mut self, spike: &Spike) -> anyhow::Result<()> {
    let proc = spike.get_proc();
    let state = proc.get_state();

    let mem_write_size = state.get_mem_write_size();
    (0..mem_write_size).for_each(|i| {
      let (addr, value, size) = state.get_mem_write(i);
      (0..size).for_each(|offset| {
        self
          .mem_access_record
          .all_writes
          .entry(addr + offset as u32)
          .or_insert(MemWriteRecord { writes: vec![], num_completed_writes: 0 })
          .writes
          .push(SingleMemWrite {
            val: (value >> (offset * 8)) as u8,
            executed: false,
          });
      });
      trace!("SpikeMemWrite: addr={addr:x}, value={value:x}, size={size}");
      if addr == 0x4000_0000 && value == 0xdead_beef && size == 4 {
        self.exit = true;
        return;
      }
    });

    Ok(())
  }
}
