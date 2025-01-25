use std::{
  fs::File,
  os::unix::fs::FileExt as _,
  path::{Path, PathBuf},
};

use anyhow::Context as _;
use clap::Parser;
use elf::{
  abi::{EM_RISCV, ET_EXEC, PT_LOAD},
  endian::LittleEndian,
  ElfStream,
};
use spike::{Spike, SpikeMemory};
use t1devices::AddressSpace;

mod spike;

#[derive(Parser)]
pub struct CliArgs {
  /// Path to the ELF file
  pub elf_file: PathBuf,

  /// ISA config
  #[arg(long)]
  pub isa: String,

  /// vlen config
  #[arg(long)]
  pub vlen: u32,
}

fn main() -> anyhow::Result<()> {
  let args = CliArgs::parse();
  let (memory, exit_flag) = t1devices::create_emu_addrspace(fake_get_cycle);
  let mut memory = Memory::new(memory);
  let elf_entry = memory.load_elf(&args.elf_file)?;
  let mut emu = Spike::new(&args.isa, args.vlen as usize, memory);
  dbg!(elf_entry);
  emu.reset_with_pc(elf_entry);
  while !exit_flag.is_finish() {
    emu.step_one();
  }
  Ok(())
}

fn fake_get_cycle() -> u64 {
  0
}

struct Memory {
  mem: t1devices::AddressSpace,
}

impl Memory {
  pub fn new(mem: AddressSpace) -> Self {
    Memory { mem }
  }

  pub fn load_elf(&mut self, path: &Path) -> anyhow::Result<u64> {
    let mem = &mut self.mem;
    let file = File::open(path).with_context(|| "reading ELF file")?;
    let mut elf: ElfStream<LittleEndian, _> =
      ElfStream::open_stream(&file).with_context(|| "parsing ELF file")?;

    if elf.ehdr.e_machine != EM_RISCV {
      anyhow::bail!("ELF is not in RISC-V");
    }

    if elf.ehdr.e_type != ET_EXEC {
      anyhow::bail!("ELF is not an executable");
    }

    if elf.ehdr.e_phnum == 0 {
      anyhow::bail!("ELF has zero size program header");
    }

    let mut load_buffer = Vec::new();
    elf.segments().iter().filter(|phdr| phdr.p_type == PT_LOAD).for_each(|phdr| {
      let vaddr: usize = phdr.p_vaddr.try_into().expect("fail converting vaddr(u64) to usize");
      let filesz: usize = phdr.p_filesz.try_into().expect("fail converting p_filesz(u64) to usize");

      // Load file start from offset into given mem slice
      // The `offset` of the read_at method is relative to the start of the file and thus independent from the current cursor.
      load_buffer.resize(filesz, 0u8);
      file.read_at(load_buffer.as_mut_slice(), phdr.p_offset).unwrap_or_else(|err| {
        panic!(
          "fail reading ELF into mem with vaddr={}, filesz={}, offset={}. Error detail: {}",
          vaddr, filesz, phdr.p_offset, err
        )
      });
      mem.write_mem(vaddr as u32, load_buffer.len() as u32, &load_buffer);
    });

    Ok(elf.ehdr.e_entry)
  }
}

impl SpikeMemory for Memory {
  fn addr_to_mem(&mut self, addr: u64) -> Option<&mut u8> {
    self.mem.addr_to_mem(addr.try_into().unwrap())
  }

  fn mmio_load(&mut self, addr: u64, data: &mut [u8]) -> bool {
    self.mem.read_mem(addr.try_into().unwrap(), data.len() as u32, data);
    true
  }

  fn mmio_store(&mut self, addr: u64, data: &[u8]) -> bool {
    self.mem.write_mem(addr.try_into().unwrap(), data.len() as u32, data);
    true
  }
}
