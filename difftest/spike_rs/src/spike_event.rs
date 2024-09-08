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
  pub is_fd_written: bool,
  pub vd_write_record: VdWriteRecord,
  pub mem_access_record: MemAccessRecord,
  pub vrf_access_record: VrfAccessRecord,

  // exit
  pub is_exit: bool,
}

impl SpikeEvent {
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
      is_fd_written: false,
      vd_write_record: Default::default(),
      mem_access_record: Default::default(),
      vrf_access_record: Default::default(),

      is_exit: false,
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
    self.is_exit
  }

  pub fn is_vfence(&self) -> bool {
    self.is_exit() // only exit instruction is treated as fence now
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
      self.pc, self.disasm, self.inst_bits
    )
  }

  pub fn get_vrf_write_range(&self, vlen_in_bytes: u32) -> anyhow::Result<(u32, u32)> {
    if self.is_vstore() {
      return Ok((0, 0));
    }

    if self.is_vload() {
      let vd_bytes_start = self.rd_idx * vlen_in_bytes;
      if self.is_whole() {
        return Ok((vd_bytes_start, vlen_in_bytes * (1 + self.vnf)));
      }
      let len = if self.vlmul() & 0b100 != 0 {
        vlen_in_bytes * (1 + self.vnf)
      } else {
        (vlen_in_bytes * (1 + self.vnf)) << self.vlmul()
      };
      return Ok((vd_bytes_start, len));
    }

    let vd_bytes_start = self.rd_idx * vlen_in_bytes;

    if self.is_mask_vd() {
      return Ok((vd_bytes_start, vlen_in_bytes));
    }

    let len = if self.vlmul() & 0b100 != 0 {
      vlen_in_bytes >> (8 - self.vlmul())
    } else {
      vlen_in_bytes << self.vlmul()
    };

    Ok((
      vd_bytes_start,
      if self.is_widening() { len * 2 } else { len },
    ))
  }

  pub fn pre_log_arch_changes(&mut self, spike: &Spike, vlen: u32) -> anyhow::Result<()> {
    if self.do_log_vrf {
      // record the vrf writes before executing the insn
      let proc = spike.get_proc();
      self.rd_bits = proc.get_state().get_reg(self.rd_idx, false);
      let (start, len) = self.get_vrf_write_range(vlen).unwrap();
      self.vd_write_record.vd_bytes.resize(len as usize, 0u8);
      for i in 0..len {
        let offset = start + i;
        let vreg_index = offset / vlen;
        let vreg_offset = offset % vlen;
        let cur_byte = proc.get_vreg_data(vreg_index, vreg_offset);
        self.vd_write_record.vd_bytes[i as usize] = cur_byte;
      }
    }

    Ok(())
  }

  pub fn log_arch_changes(&mut self, spike: &Spike, vlen: u32) -> anyhow::Result<()> {
    if self.do_log_vrf {
      self.log_vrf_write(spike, vlen).unwrap();
      self.log_reg_write(spike).unwrap();
    }
    self.log_mem_write(spike).unwrap();
    self.log_mem_read(spike).unwrap();

    Ok(())
  }

  fn log_vrf_write(&mut self, spike: &Spike, vlen: u32) -> anyhow::Result<()> {
    let proc = spike.get_proc();
    // record vrf writes
    // note that we do not need log_reg_write to find records, we just decode the
    // insn and compare bytes
    let vlen_in_bytes = vlen / 8;
    let (start, len) = self.get_vrf_write_range(vlen_in_bytes).unwrap();
    trace!("vrf write range: start: {start}, len: {len}");
    for i in 0..len {
      let offset = start + i;
      let origin_byte = self.vd_write_record.vd_bytes[i as usize];
      let vreg_index = offset / vlen_in_bytes;
      let vreg_offset = offset % vlen_in_bytes;
      let cur_byte = proc.get_vreg_data(vreg_index, vreg_offset);
      if origin_byte != cur_byte {
        self
          .vrf_access_record
          .all_writes
          .entry(offset as usize)
          .or_insert(SingleVrfWrite { byte: cur_byte, executed: false });
        trace!(
          "SpikeVRFChange: vrf={:?}, change_from={origin_byte}, change_to={cur_byte}, vrf_idx={offset}",
          vec![offset / vlen_in_bytes, offset % vlen_in_bytes],
        );
      } else {
        trace!(
          "SpikeVRFChange: vrf={:?}, change_from={origin_byte}, not changed, vrf_idx={offset}",
          vec![offset / vlen_in_bytes, offset % vlen_in_bytes],
        );
      }
    }
    Ok(())
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
    (0..reg_write_size).for_each(|idx| {
      let rd_idx_type = state.get_reg_write_index(idx);
      match rd_idx_type & 0xf {
        0b0000 => {
          // scalar rf
          self.rd_idx = rd_idx_type >> 4;
          if self.rd_idx != 0 {
            let data = state.get_reg(self.rd_idx, false);
            self.is_rd_written = true;
            self.rd_bits = data;
            trace!(
              "ScalarRFChange: idx={:#02x}, data={:08x}",
              self.rd_idx,
              self.rd_bits
            );
          }
        }
        0b0001 => {
          self.rd_idx = rd_idx_type >> 4;
          let data = state.get_reg(self.rd_idx, true);
          self.is_fd_written = true;
          self.rd_bits = data;
          trace!(
            "FloatRFChange: idx={:#02x}, data={:08x}",
            self.rd_idx,
            self.rd_bits
          );
        }
        _ => trace!(
          "UnknownRegChange, idx={:#02x}, spike detect unknown reg change",
          self.rd_idx
        ),
      }
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

      if addr == 0x4000_0000 && value == 0xdead_beef {
        trace!("SpikeExit: exit by writing 0xdeadbeef to 0x40000000");
        self.is_exit = true;

        return;
      }
    });

    Ok(())
  }

  fn log_mem_read(&mut self, spike: &Spike) -> anyhow::Result<()> {
    let proc = spike.get_proc();
    let state = proc.get_state();

    let mem_read_size = state.get_mem_read_size();
    (0..mem_read_size).for_each(|i| {
      let (addr, size) = state.get_mem_read(i);
      let mut value = 0;
      (0..size).for_each(|offset| {
        let byte = spike.mem_byte_on_addr(addr as usize + offset as usize).unwrap();
        value |= (byte as u64) << (offset * 8);
        // record the read
        self
          .mem_access_record
          .all_reads
          .entry(addr + offset as u32)
          .or_insert(MemReadRecord { reads: vec![], num_completed_reads: 0 })
          .reads
          .push(SingleMemRead { val: byte, executed: false });
      });
      trace!("SpikeMemRead: addr={addr:08x}, value={value:08x}, size={size}");
    });

    Ok(())
  }

  pub fn check_rd(&self, data: u32) -> anyhow::Result<()> {
    // TODO: rtl should indicate whether resp_bits_data is valid
    if self.is_rd_written {
      assert_eq!(
        data, self.rd_bits,
        "expect to write rd[{}] = {}, actual {}",
        self.rd_idx, self.rd_bits, data
      );
    }

    Ok(())
  }

  pub fn check_is_ready_for_commit(&self, cycle: u64) -> anyhow::Result<()> {
    for (addr, record) in &self.mem_access_record.all_writes {
      assert_eq!(
        record.num_completed_writes,
        record.writes.len(),
        "[{cycle}] expect to write mem {addr:#x}, not executed when commit, issue_idx={} ({})",
        self.issue_idx,
        self.describe_insn(),
      );
    }
    for (idx, record) in &self.vrf_access_record.all_writes {
      assert!(
        record.executed,
        "[{cycle}] expect to write vrf {idx}, not executed when commit, issue_idx={} ({})",
        self.issue_idx,
        self.describe_insn()
      );
    }

    Ok(())
  }
}
