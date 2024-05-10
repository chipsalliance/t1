use lazy_static::lazy_static;
use std::collections::HashMap;
use std::fs::File;
use std::io::Read;
use std::path::Path;
use std::sync::Mutex;
use tracing::{info, trace};
use xmas_elf::{
	header,
	program::{ProgramHeader, Type},
	ElfFile,
};

mod libspike_interfaces;
use libspike_interfaces::*;

use super::Config;

// read the addr from spike memory
// caller should make sure the address is valid
#[no_mangle]
pub extern "C" fn rs_addr_to_mem(addr: u64) -> *mut u8 {
	let addr = addr as usize;
	let mut spike_mem = SPIKE_MEM.lock().unwrap();
	let spike_mut = spike_mem.as_mut().unwrap();
	&mut spike_mut.mem[addr] as *mut u8
}

pub struct SpikeMem {
	pub mem: Vec<u8>,
	pub size: usize,
}

lazy_static! {
	static ref SPIKE_MEM: Mutex<Option<Box<SpikeMem>>> = Mutex::new(None);
}

fn init_memory(size: usize) {
	let mut spike_mem = SPIKE_MEM.lock().unwrap();
	if spike_mem.is_none() {
		info!("Creating SpikeMem with size: 0x{:x}", size);
		*spike_mem = Some(Box::new(SpikeMem {
			mem: vec![0; size],
			size,
		}));
	}
}

fn ld(addr: usize, len: usize, bytes: Vec<u8>) -> anyhow::Result<()> {
	trace!("ld: addr: 0x{:x}, len: 0x{:x}", addr, len);
	let mut spike_mem = SPIKE_MEM.lock().unwrap();
	let spike_ref = spike_mem.as_mut().unwrap();

	assert!(addr + len <= spike_ref.size);

	let dst = &mut spike_ref.mem[addr..addr + len];
	for (i, byte) in bytes.iter().enumerate() {
		dst[i] = *byte;
	}

	Ok(())
}

fn read_mem(addr: usize) -> anyhow::Result<u8> {
	let mut spike_mem = SPIKE_MEM.lock().unwrap();
	let spike_ref = spike_mem.as_mut().unwrap();

	let dst = &mut spike_ref.mem[addr];

	Ok(*dst)
}

fn load_elf(fname: &Path) -> anyhow::Result<u64> {
	let mut file = File::open(fname).unwrap();
	let mut buffer = Vec::new();
	file.read_to_end(&mut buffer).unwrap();

	let elf_file = ElfFile::new(&buffer).unwrap();

	let header = elf_file.header;
	assert_eq!(header.pt2.machine().as_machine(), header::Machine::RISC_V);
	assert_eq!(header.pt1.class(), header::Class::ThirtyTwo);

	for ph in elf_file.program_iter() {
		match ph {
			ProgramHeader::Ph32(ph) => {
				if ph.get_type() == Ok(Type::Load) {
					let offset = ph.offset as usize;
					let size = ph.file_size as usize;
					let addr = ph.virtual_addr as usize;

					let slice = &buffer[offset..offset + size];
					ld(addr, size, slice.to_vec()).unwrap();
				}
			}
			_ => (),
		}
	}

	Ok(header.pt2.entry_point())
}

pub fn clip(binary: u64, a: i32, b: i32) -> u32 {
	assert!(a <= b, "a should be less than or equal to b");
	let nbits = b - a + 1;
	let mask = if nbits >= 32 {
		u32::MAX
	} else {
		(1 << nbits) - 1
	};
	(binary as u32 >> a) & mask
}

struct MemLog {
	addr: u64,
	value: u64,
	size: u8,
}

struct SingleMemWrite {
	val: u8,
	executed: bool, // set to true when rtl execute this mem access
}

struct SingleMemRead {
	val: u8,
	executed: bool, // set to true when rtl execute this mem access
}

struct MemWriteRecord {
	writes: Vec<SingleMemWrite>,
	num_completed_writes: i32,
}

struct MemReadRecord {
	reads: Vec<SingleMemRead>,
	num_completed_reads: i32,
}

struct SingleVrfWrite {
	byte: u8,
	executed: bool, // set to true when rtl execute this mem access
}

#[derive(Default)]
struct VdWriteRecord {
	vd_bytes: Vec<u8>,
}

#[derive(Default)]
struct MemAccessRecord {
	all_writes: HashMap<u32, MemWriteRecord>,
	all_reads: HashMap<u32, MemReadRecord>,
}

#[derive(Default)]
struct VrfAccessRecord {
	all_writes: HashMap<u32, SingleVrfWrite>,
}

#[derive(Default)]
pub struct SpikeEvent {
	// spike mem_read_info_t
	mem_read_info: Vec<(u32, u64, u8)>,

	// replace with actual struct name
	log_mem_queue: Vec<MemLog>,

	lsu_idx: u8,
	issue_idx: u8,

	is_issued: bool,

	is_load: bool,
	is_store: bool,
	is_whole: bool,
	is_widening: bool,
	is_mask_vd: bool,
	is_exit_insn: bool,
	is_vfence_insn: bool,

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
		let (rs1, rs2) = proc.get_rs();
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
}

pub struct SpikeHandle {
	spike: Spike,
	pub spike_event: Option<SpikeEvent>,
}

impl SpikeHandle {
	pub fn new(size: usize, fname: &Path) -> Self {
		// register the addr_to_mem callback
		unsafe { spike_register_callback(rs_addr_to_mem) }

		// create a new spike memory instance
		init_memory(size);

		// load the elf file
		let entry_addr = load_elf(fname).unwrap();

		// initialize spike
		let arch = "vlen:1024,elen:32";
		let set = "rv32gcv";
		let lvl = "M";

		let spike = Spike::new(arch, set, lvl);

		// initialize processor
		let proc = spike.get_proc();
		let state = proc.get_state();
		proc.reset();
		state.set_pc(entry_addr);

		let spike_event = SpikeEvent::new(&spike);
		SpikeHandle { spike, spike_event }
	}

	// execute the spike processor for one instruction and
	// update pc with the new pc
	pub fn exec(&self) -> anyhow::Result<()> {
		let spike = &self.spike;
		let proc = spike.get_proc();
		let state = proc.get_state();

		let pc = state.get_pc();
		let disasm = proc.disassemble();

		// info!("pc: 0x{:x}, disasm: {:?}", pc, disasm);

		let new_pc = proc.func();

		state.handle_pc(new_pc).unwrap();

		let ret = state.exit();

		if ret == 0 {
			return Err(anyhow::anyhow!("simulation finished!"));
		}

		Ok(())
	}

	// step the spike processor until the instruction is load/store/v
	pub fn step(&mut self, config: &Config) -> anyhow::Result<()> {
		loop {
			self.exec()?;

			let spike = &self.spike;
			let proc = spike.get_proc();

			let insn = proc.get_insn();

			let opcode = clip(insn, 0, 6);
			let width = clip(insn, 12, 14);
			let rs1 = clip(insn, 15, 19);
			let csr = clip(insn, 20, 31);

			let is_load_type = opcode == 0b0000111 && (((width - 1) & 0b100) != 0);
			let is_store_type = opcode == 0b0100111 && (((width - 1) & 0b100) != 0);
			let is_v_type = opcode == 0b1010111;

			let is_csr_type = opcode == 0b1110011 && ((width & 0b011) != 0);
			let is_csr_write = is_csr_type && (((width & 0b100) | rs1) != 0);
			let is_vsetvl = opcode == 0b1010111 && width == 0b111;

			if (is_load_type || is_store_type || is_v_type || (is_csr_write && csr == 0x7cc))
				&& !is_vsetvl
			{
				self.pre_log_arch_changes(config)?;
				self.exec()?;
				self.log_arch_changes(config)?;
				return Ok(());
			}
		}
	}

	pub fn get_vrf_write_range(&self, vlen_in_bytes: u32) -> anyhow::Result<(u32, u32)> {
		let se = self.spike_event.as_ref().unwrap();
		if se.is_store {
			return Ok((0, 0));
		}

		if se.is_load {
			let vd_bytes_start = se.rd_idx * vlen_in_bytes;
			if se.is_whole {
				return Ok((vd_bytes_start, vlen_in_bytes * (1 + se.vnf)));
			}
			let len = if se.vlmul & 0b100 != 0 {
				vlen_in_bytes * (1 + se.vnf)
			} else {
				vlen_in_bytes * (1 + se.vnf) << se.vlmul
			};
			return Ok((vd_bytes_start, len));
		}

		let vd_bytes_start = se.rd_idx * vlen_in_bytes;

		if se.is_mask_vd {
			return Ok((vd_bytes_start, vlen_in_bytes));
		}

		let len = if se.vlmul & 0b100 != 0 {
			vlen_in_bytes >> (8 - se.vlmul)
		} else {
			vlen_in_bytes << se.vlmul
		};

		return Ok((vd_bytes_start, if se.is_widening { len * 2 } else { len }));
	}

	pub fn pre_log_arch_changes(&mut self, config: &Config) -> anyhow::Result<()> {
		// record the vrf writes before executing the insn
		let vlen_in_bytes = config.parameter.vLen / 8;
		let (start, len) = self.get_vrf_write_range(vlen_in_bytes).unwrap();
		let vrf_addr_raw = self.spike.get_proc().get_vreg_addr();
		let vrf_addr = unsafe {
			std::slice::from_raw_parts(vrf_addr_raw.wrapping_add(start as usize), len as usize)
		};
		trace!(
			"vrf_addr: {:p}, start: {}, len: {}",
			vrf_addr_raw,
			start,
			len
		);
		let vd_bytes = vrf_addr[0 as usize..len as usize].to_vec();

		let se = self.spike_event.as_mut().unwrap();
		if se.is_rd_fp {
			se.rd_bits = self.spike.get_proc().get_rd();
		}
		se.vd_write_record.vd_bytes = vd_bytes;

		Ok(())
	}

	fn log_mem_write(&mut self) -> anyhow::Result<()> {
		let spike = &self.spike;
		let proc = spike.get_proc();
		let state = proc.get_state();

		let mem_write_size = state.get_mem_write_size();
		(0..mem_write_size).for_each(|i| {
			let (addr, value, size) = state.get_mem_write(i);
			let se = self.spike_event.as_mut().unwrap();
			(0..size).for_each(|offset| {
				se.mem_access_record.all_writes.insert(
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

	fn log_mem_read(&mut self) -> anyhow::Result<()> {
		let spike = &self.spike;
		let proc = spike.get_proc();
		let state = proc.get_state();

		let mem_read_size = state.get_mem_read_size();
		(0..mem_read_size).for_each(|i| {
			let (addr, size) = state.get_mem_read(i);
			let se = self.spike_event.as_mut().unwrap();
			let mut value = 0;
			(0..size).for_each(|offset| {
				let byte = read_mem(addr as usize + offset as usize).unwrap();
				value |= (byte as u64) << (offset * 8);
				se.mem_access_record.all_reads.insert(
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

	pub fn log_arch_changes(&mut self, config: &Config) -> anyhow::Result<()> {
		let spike = &self.spike;
		let proc = spike.get_proc();

		// record vrf writes
		// note that we do not need log_reg_write to find records, we just decode the
		// insn and compare bytes
		let vlen_in_bytes = config.parameter.vLen / 8;
		let vrf_addr = proc.get_vreg_addr();
		let (start, len) = self.get_vrf_write_range(vlen_in_bytes).unwrap();
		for i in 0..len {
			let offset = start + i;
			let se = self.spike_event.as_mut().unwrap();
			let origin_byte = se.vd_write_record.vd_bytes[i as usize];
			let cur_byte = unsafe { *vrf_addr.offset(offset as isize) };
			if origin_byte != cur_byte {
				// se.vrf_access_record.all_writes[offset as usize] = cur_byte;
				se.vrf_access_record.all_writes.insert(
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

		self.log_mem_write().unwrap();
		self.log_mem_read().unwrap();

		Ok(())
	}

	pub fn peek_issue(&mut self, idx: u32) -> anyhow::Result<()> {
		let se = self.spike_event.as_mut().unwrap();
		if se.is_vfence_insn || se.is_exit_insn {
			return Ok(());
		}

		se.is_issued = true;
		se.issue_idx = 0;

		info!("PeekIssue: idx={}", idx);

		Ok(())
	}
}
