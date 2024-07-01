use super::Spike;
use super::{clip, read_mem};
use std::collections::HashMap;
use tracing::{info, trace};

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
}

#[derive(Default, Debug, Clone)]
pub struct SpikeEvent {
  pub lsu_idx: u8,
  pub issue_idx: u8,

  pub is_issued: bool,

  pub is_load: bool,
  pub is_store: bool,
  pub is_whole: bool,
  pub is_widening: bool,
  pub is_mask_vd: bool,
  pub is_exit_insn: bool,
  pub is_vfence_insn: bool,

  pub pc: u64,
  pub inst_bits: u64,

  // scalar to vector interface(used for driver)
  pub rs1_bits: u32,
  pub rs2_bits: u32,
  pub rd_idx: u32,

  // vtype
  pub vsew: u32,
  pub vlmul: u32,
  pub vma: bool,
  pub vta: bool,
  pub vxrm: u32,
  pub vnf: u32,

  // other CSR
  pub vill: bool,
  pub vxsat: bool,

  pub vl: u32,
  pub vstart: u16,
  pub disasm: String,

  pub vd_write_record: VdWriteRecord,

  pub is_rd_written: bool,
  pub rd_bits: u32,
  pub is_rd_fp: bool, // whether rd is a fp register

  pub mem_access_record: MemAccessRecord,
  pub vrf_access_record: VrfAccessRecord,
}

impl SpikeEvent {
  pub fn new(spike: &Spike) -> Option<Self> {
    let inst_bits = spike.get_proc().get_insn();
    // inst info
    let opcode = clip(inst_bits, 0, 6);
    let width = clip(inst_bits, 12, 14); // also funct3
    let funct6 = clip(inst_bits, 26, 31);
    let mop = clip(inst_bits, 26, 27);
    let lumop = clip(inst_bits, 20, 24);
    let vm = clip(inst_bits, 25, 25);

    // rs1, rs2
    let is_rs_fp = opcode == 0b1010111 && width == 0b101/* OPFVF */;
    let proc = spike.get_proc();
    let state = proc.get_state();
    let (rs1, rs2) = (proc.get_rs1(), proc.get_rs2());

    // vtype
    let vtype = proc.vu_get_vtype();

    Some(SpikeEvent {
      lsu_idx: 255,
      issue_idx: 255,
      inst_bits,
      rs1_bits: state.get_reg(rs1, is_rs_fp),
      rs2_bits: state.get_reg(rs2, is_rs_fp),
      // rd
      is_rd_fp: (opcode == 0b1010111)
        && (rs1 == 0)
        && (funct6 == 0b010000)
        && (vm == 1)
        && (width == 0b001),
      rd_idx: proc.get_rd(),
      is_rd_written: false,

      // vtype
      vlmul: clip(vtype, 0, 2),
      vma: clip(vtype, 7, 7) != 0,
      vta: clip(vtype, 6, 6) != 0,
      vsew: clip(vtype, 3, 5),
      vxrm: proc.vu_get_vxrm(),
      vnf: proc.vu_get_vnf(),

      vill: proc.vu_get_vill(),
      vxsat: proc.vu_get_vxsat(),
      vl: proc.vu_get_vl(),
      vstart: proc.vu_get_vstart(),

      // se info
      disasm: spike.get_proc().disassemble(),
      pc: proc.get_state().get_pc(),
      is_load: opcode == 0b0000111,
      is_store: opcode == 0b0100111,
      is_whole: mop == 0 && lumop == 8,
      is_widening: opcode == 0b1010111 && (funct6 >> 4) == 0b11,
      is_mask_vd: opcode == 0b1010111 && (funct6 >> 3 == 0b011 || funct6 == 0b010001),
      is_exit_insn: opcode == 0b1110011,
      is_vfence_insn: false,

      is_issued: false,
      ..Default::default()
    })
  }

  pub fn get_vrf_write_range(&self, vlen_in_bytes: u32) -> anyhow::Result<(u32, u32)> {
    if self.is_store {
      return Ok((0, 0));
    }

    if self.is_load {
      let vd_bytes_start = self.rd_idx * vlen_in_bytes;
      if self.is_whole {
        return Ok((vd_bytes_start, vlen_in_bytes * (1 + self.vnf)));
      }
      let len = if self.vlmul & 0b100 != 0 {
        vlen_in_bytes * (1 + self.vnf)
      } else {
        (vlen_in_bytes * (1 + self.vnf)) << self.vlmul
      };
      return Ok((vd_bytes_start, len));
    }

    let vd_bytes_start = self.rd_idx * vlen_in_bytes;

    if self.is_mask_vd {
      return Ok((vd_bytes_start, vlen_in_bytes));
    }

    let len = if self.vlmul & 0b100 != 0 {
      vlen_in_bytes >> (8 - self.vlmul)
    } else {
      vlen_in_bytes << self.vlmul
    };

    Ok((vd_bytes_start, if self.is_widening { len * 2 } else { len }))
  }

  pub fn pre_log_arch_changes(&mut self, spike: &Spike, vlen: u32) -> anyhow::Result<()> {
    self.rd_bits = spike.get_proc().get_rd();

    // record the vrf writes before executing the insn
    let vlen_in_bytes = vlen;

    let proc = spike.get_proc();
    let (start, len) = self.get_vrf_write_range(vlen_in_bytes).unwrap();
    self.vd_write_record.vd_bytes.resize(len as usize, 0u8);
    for i in 0..len {
      let offset = start + i;
      let vreg_index = offset / vlen_in_bytes;
      let vreg_offset = offset % vlen_in_bytes;
      let cur_byte = proc.get_vreg_data(vreg_index, vreg_offset);
      self.vd_write_record.vd_bytes[i as usize] = cur_byte;
    }

    Ok(())
  }

  pub fn log_arch_changes(&mut self, spike: &Spike, vlen: u32) -> anyhow::Result<()> {
    self.log_vrf_write(spike, vlen).unwrap();
    self.log_reg_write(spike).unwrap();
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
    trace!("start: {start}, len: {len}");
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
          .or_insert(SingleVrfWrite {
            byte: cur_byte,
            executed: false,
          });
        trace!(
          "SpikeVRFChange: vrf={:?}, change_from={origin_byte}, change_to={cur_byte}, vrf_idx={offset}",
          vec![offset / vlen_in_bytes, offset % vlen_in_bytes],
        );
      }
    }
    Ok(())
  }

  fn log_reg_write(&mut self, spike: &Spike) -> anyhow::Result<()> {
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
        if data != self.rd_bits {
          trace!(
            "ScalarRFChange: idx={}, change_from={}, change_to={data}",
            self.rd_idx, self.rd_bits
          );
          self.rd_bits = data;
          self.is_rd_written = true;
        }
      }
      0b0001 => {
        let data = state.get_reg(self.rd_idx, true);
        if data != self.rd_bits {
          trace!(
            "FloatRFChange: idx={}, change_from={}, change_to={data}",
            self.rd_idx, self.rd_bits
          );
          self.rd_bits = data;
          self.is_rd_written = true;
        }
      }
      _ => trace!("UnknownRegChange, idx={:08x}, spike detect unknown reg change", state.get_reg_write_index(idx)),
    });

    Ok(())
  }

  fn log_mem_write(&mut self, spike: &Spike) -> anyhow::Result<()> {
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
          .or_insert(MemWriteRecord {
            writes: vec![],
            num_completed_writes: 0,
          })
          .writes
          .push(SingleMemWrite {
            val: (value >> (offset * 8)) as u8,
            executed: false,
          });
      });
      info!("SpikeMemWrite: addr={addr:x}, value={value:x}, size={size}");
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
        let byte = read_mem(addr as usize + offset as usize).unwrap();
        value |= (byte as u64) << (offset * 8);
        // record the read
        self
          .mem_access_record
          .all_reads
          .entry(addr + offset as u32)
          .or_insert(MemReadRecord {
            reads: vec![],
            num_completed_reads: 0,
          })
          .reads
          .push(SingleMemRead {
            val: byte,
            executed: false,
          });
      });
      info!("SpikeMemRead: addr={addr:x}, value={value:x}, size={size}");
    });

    Ok(())
  }

  pub fn record_rd_write(&self, data: u32) -> anyhow::Result<()> {
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

  pub fn check_is_ready_for_commit(&self, cycle: usize) -> anyhow::Result<()> {
    for (addr, record) in &self.mem_access_record.all_writes {
      assert_eq!(
        record.num_completed_writes,
        record.writes.len(),
        "[{cycle}] expect to write mem {addr:#x}, not executed when commit (pc={:#x}, inst={})",
        self.pc,
        self.disasm
      );
    }
    for (idx, record) in &self.vrf_access_record.all_writes {
      assert!(
        record.executed,
        "[{cycle}] expect to write vrf {idx}, not executed when commit (pc={:#x}, inst={})",
        self.pc, self.disasm
      );
    }

    Ok(())
  }
}
