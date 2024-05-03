use crate::info;
use lazy_static::lazy_static;
use std::fs::File;
use std::path::Path;
use std::io::Read;
use std::sync::Mutex;
use xmas_elf::{
	header,
	program::{ProgramHeader, Type},
	ElfFile,
};

mod libspike_interfaces;
use libspike_interfaces::*;

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
	info!("ld: addr: 0x{:x}, len: 0x{:x}", addr, len);
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
	let mask = if nbits >= 32 { u32::MAX } else { (1 << nbits) - 1 };
	(binary as u32 >> a) & mask
}

pub struct SpikeHandle {
	spike: Spike,
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

		SpikeHandle { spike }
	}

	// execute the spike processor for one instruction and 
	// update pc with the new pc
	pub fn exec(&self) -> anyhow::Result<()> {
		let spike = &self.spike;
		let proc = spike.get_proc();
		let state = proc.get_state();

		let pc = state.get_pc();
		let disasm = proc.disassemble(pc);

		info!("pc: 0x{:x}, disasm: {:?}", pc, disasm);

		let new_pc = proc.func(pc);

		state.handle_pc(new_pc).unwrap();

		let ret = state.exit();

		if ret == 0 {
			return Err(anyhow::anyhow!("simulation finished!"));
		}

		Ok(())
	}

	// step the spike processor until the instruction is load/store/v
	pub fn step(&self) -> anyhow::Result<()> {
		loop {
			self.exec()?;
	
			let spike = &self.spike;
			let proc = spike.get_proc();
			let pc = proc.get_state().get_pc();
	
			let insn = proc.get_insn(pc);
	
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
	
			if (is_load_type || is_store_type || is_v_type || (is_csr_write && csr == 0x7cc)) && !is_vsetvl {
				dbg!(format!("{:#x}", insn));
				self.exec()?;
				return Ok(());
			}
		}
	}
}
