use super::Spike;
use super::{clip, read_mem};
use std::collections::HashMap;
use tracing::{info, trace};

#[derive(Debug, Clone)]
struct MemLog {
	addr: u64,
	value: u64,
	size: u8,
}

#[derive(Debug, Clone)]
struct SingleMemWrite {
	val: u8,
	executed: bool, // set to true when rtl execute this mem access
}

#[derive(Debug, Clone)]
struct SingleMemRead {
	val: u8,
	executed: bool, // set to true when rtl execute this mem access
}

#[derive(Debug, Clone)]
struct MemWriteRecord {
	writes: Vec<SingleMemWrite>,
	num_completed_writes: i32,
}

#[derive(Debug, Clone)]
struct MemReadRecord {
	reads: Vec<SingleMemRead>,
	num_completed_reads: i32,
}

#[derive(Debug, Clone)]
struct SingleVrfWrite {
	byte: u8,
	executed: bool, // set to true when rtl execute this mem access
}

#[derive(Default, Debug, Clone)]
struct VdWriteRecord {
	vd_bytes: Vec<u8>,
}

#[derive(Default, Debug, Clone)]
struct MemAccessRecord {
	all_writes: HashMap<u32, MemWriteRecord>,
	all_reads: HashMap<u32, MemReadRecord>,
}

#[derive(Default, Debug, Clone)]
struct VrfAccessRecord {
	all_writes: HashMap<u32, SingleVrfWrite>,
}

#[derive(Default, Debug, Clone)]
pub struct SpikeEvent {
	// replace with actual struct name
	// log_mem_queue: Vec<MemLog>,
	lsu_idx: u8,
	pub issue_idx: u8,

	pub is_issued: bool,

	is_load: bool,
	is_store: bool,
	is_whole: bool,
	is_widening: bool,
	is_mask_vd: bool,
	pub is_exit_insn: bool,
	pub is_vfence_insn: bool,

	pc: u64,
	inst_bits: u64,

	// scalar to vector interface(used for driver)
	rs1_bits: u32,
	rs2_bits: u32,
	rd_idx: u32,

	// vtype
	vsew: u32,
	vlmul: u32,
	vma: bool,
	vta: bool,
	vxrm: u32,
	vnf: u32,

	// other CSR
	vill: bool,
	vxsat: bool,

	vl: u32,
	vstart: u16,

	vd_write_record: VdWriteRecord,

	is_rd_written: bool,
	rd_bits: u32,
	is_rd_fp: bool, // whether rd is a fp register

	mem_access_record: MemAccessRecord,
	vrf_access_record: VrfAccessRecord,
}

impl SpikeEvent {
	pub fn new(spike: &Spike) -> Option<Self> {
		let mut se = SpikeEvent::default();
		se.lsu_idx = 255;
		se.issue_idx = 255;

		se.inst_bits = spike.get_proc().get_insn();

		let opcode = clip(se.inst_bits, 0, 6);
		let width = clip(se.inst_bits, 12, 14); // also funct3
		let funct6 = clip(se.inst_bits, 26, 31);
		let mop = clip(se.inst_bits, 26, 27);
		let lumop = clip(se.inst_bits, 20, 24);
		let vm = clip(se.inst_bits, 25, 25);

		let proc = spike.get_proc();
		(se.rs1_bits, se.rs2_bits) = proc.get_rs_bits();
		let (rs1, _) = proc.get_rs();
		let rd = proc.get_rd();

		se.is_rd_fp =
			(opcode == 0b1010111) && (rs1 == 0) && (funct6 == 0b010000) && (vm == 1) && (width == 0b001);
		se.rd_idx = rd;

		se.is_rd_written = false;

		let vtype = proc.vu_get_vtype();
		se.vlmul = clip(vtype, 0, 2);
		se.vma = clip(vtype, 7, 7) != 0;
		se.vta = clip(vtype, 6, 6) != 0;
		se.vsew = clip(vtype, 3, 5);
		se.vxrm = proc.vu_get_vxrm();
		se.vnf = proc.vu_get_vnf();

		se.vill = proc.vu_get_vill();
		se.vxsat = proc.vu_get_vxsat();
		se.vl = proc.vu_get_vl();
		se.vstart = proc.vu_get_vstart();

		se.pc = proc.get_state().get_pc();
		se.is_load = opcode == 0b0000111;
		se.is_store = opcode == 0b0100111;
		se.is_whole = mop == 0 && lumop == 8;
		se.is_widening = opcode == 0b1010111 && (funct6 >> 4) == 0b11;
		se.is_mask_vd = opcode == 0b1010111 && (funct6 >> 3 == 0b011 || funct6 == 0b010001);
		se.is_exit_insn = opcode == 0b1110011;
		se.is_vfence_insn = false;

		se.is_issued = false;
		Some(se)
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
				vlen_in_bytes * (1 + self.vnf) << self.vlmul
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

		return Ok((vd_bytes_start, if self.is_widening { len * 2 } else { len }));
	}

	pub fn pre_log_arch_changes(&mut self, spike: &Spike, vlen: u32) -> anyhow::Result<()> {
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

		if self.is_rd_fp {
			self.rd_bits = spike.get_proc().get_rd();
		}
		Ok(())
	}

	pub fn log_arch_changes(&mut self, spike: &Spike, vlen: u32) -> anyhow::Result<()> {
		self.log_vrf_write(spike, vlen).unwrap();
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
		trace!("start: {}, len: {}", start, len);
		for i in 0..len {
			let offset = start + i;
			let origin_byte = self.vd_write_record.vd_bytes[i as usize];
			let vreg_index = offset / vlen_in_bytes;
			let vreg_offset = offset % vlen_in_bytes;
			let cur_byte = proc.get_vreg_data(vreg_index, vreg_offset);
			if origin_byte != cur_byte {
				self.vrf_access_record.all_writes.insert(
					offset,
					SingleVrfWrite {
						byte: cur_byte,
						executed: false,
					},
				);
				info!(
					"SpikeVRFChange: vrf={:?}, change_from={}, change_to={}, vrf_idx={}",
					vec![offset / vlen_in_bytes, offset % vlen_in_bytes],
					origin_byte,
					cur_byte,
					offset
				);
			}
		}
		Ok(())
	}

	fn log_mem_write(&mut self, spike: &Spike) -> anyhow::Result<()> {
		let proc = spike.get_proc();
		let state = proc.get_state();

		let mem_write_size = state.get_mem_write_size();
		(0..mem_write_size).for_each(|i| {
			let (addr, value, size) = state.get_mem_write(i);
			(0..size).for_each(|offset| {
				self.mem_access_record.all_writes.insert(
					addr + offset as u32,
					MemWriteRecord {
						writes: vec![SingleMemWrite {
							val: (value >> offset * 8) as u8,
							executed: false,
						}],
						num_completed_writes: 0,
					},
				);
			});
			info!(
				"SpikeMemWrite: addr={:x}, value={:x}, size={}",
				addr, value, size
			);
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
				self.mem_access_record.all_reads.insert(
					addr + offset as u32,
					MemReadRecord {
						reads: vec![SingleMemRead {
							val: byte,
							executed: false,
						}],
						num_completed_reads: 0,
					},
				);
			});
			info!(
				"SpikeMemRead: addr={:x}, value={:x}, size={}",
				addr, value, size
			);
		});

		Ok(())
	}
}
