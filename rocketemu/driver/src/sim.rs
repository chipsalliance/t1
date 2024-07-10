use clap::{arg, Parser};
use tracing::{info, debug, trace};
use std::collections::HashMap;
use std::os::unix::fs::FileExt;
use std::{
  fs,
  path::{Path, PathBuf},
};

use anyhow::Context;
use elf::abi::STT_FUNC;
use elf::{
  abi::{EM_RISCV, ET_EXEC, PT_LOAD},
  endian::LittleEndian,
  ElfStream,
};

pub(crate) struct AxiReadPayload {
  pub(crate) data: Vec<u8>,
}

#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
pub struct SimulationArgs {
  /// Path to the ELF file
  #[arg(long)]
  pub elf_file: PathBuf,

  /// Path to the log file
  #[arg(long)]
  pub log_file: Option<PathBuf>,

  /// Log level: trace, debug, info, warn, error
  #[arg(long, default_value = "info")]
  pub log_level: String,
}

// FIXME: fix FunctionSym
#[derive(Debug)]
#[allow(dead_code)]
pub struct FunctionSym {
  #[allow(dead_code)]
  pub(crate) name: String,
  #[allow(dead_code)]
  pub(crate) info: u8,
}
pub type FunctionSymTab = HashMap<u64, FunctionSym>;

const SIM_MEM_SIZE: usize = 1usize << 32;
const RESET_VECTOR_ADDR: usize = 10_000;

#[derive(Debug)]
pub struct Simulator {
  pub(crate) mem: Vec<u8>,
  #[allow(dead_code)]
  pub(crate) fn_sym_tab: FunctionSymTab,
  pub(crate) dlen: u32,
}

pub static WATCHDOG_CONTINUE: u8 = 0;
pub static WATCHDOG_TIMEOUT: u8 = 1;

impl Simulator {
  pub fn new(args: SimulationArgs) -> Self {
    let (mem, fn_sym_tab) = Self::load_elf(&args.elf_file).expect("fail creating simulator");

    Self {
      mem,
      fn_sym_tab,
      dlen: option_env!("DESIGN_DLEN")
        .map(|dlen| dlen.parse().expect("fail to parse dlen into u32 digit"))
        .unwrap_or(256),
    }
  }

  // FIXME: In current implementation, all the ELF sections are read without considering bytes order.
  // We might want to take care of those information with lenntoho to convert them into host byte.
  // The *elf* crate hopefully will handle this for us, but I don't do further investigation yet. (assign to @Avimitin)
  pub fn load_elf(path: &Path) -> anyhow::Result<(Vec<u8>, FunctionSymTab)> {
    let file = fs::File::open(path).with_context(|| "reading ELF file")?;
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

    // FIXME:
    // 1. If we use reduce map instead of manipulating mutable memory, does it affect
    // runtime overhead? Does rustc help us optimize this operation?
    // 2. The default ProgramHeader us u64 for Elf32_phdr and Elf64_phdr.
    let mut mem: Vec<u8> = vec![0; SIM_MEM_SIZE];
    elf.segments().iter().filter(|phdr| phdr.p_type == PT_LOAD).for_each(|phdr| {
      let vaddr: usize = phdr.p_vaddr.try_into().expect("fail converting vaddr(u64) to usize");
      let addr = RESET_VECTOR_ADDR + vaddr;
      let filesz: usize = phdr.p_filesz.try_into().expect("fail converting p_filesz(u64) to usize");
      // The `offset` of the read_at method is relative to the start of the file and thus independent from the current cursor.
      file.read_at(&mut mem[addr..addr + filesz], phdr.p_offset).unwrap_or_else(|err| {
        panic!(
          "fail reading ELF into mem with vaddr={}, filesz={}, offset={}. Error detail: {}",
          vaddr, filesz, phdr.p_offset, err
        )
      });
    });

    // FIXME: now the symbol table doesn't contain any function value
    let mut fn_sym_tab = FunctionSymTab::new();
    let symbol_table =
      elf.symbol_table().with_context(|| "reading symbol table(SHT_SYMTAB) from ELF")?;
    if let Some((parsed_table, string_table)) = symbol_table {
      parsed_table
        .iter()
        // st_symtype = symbol.st_info & 0xf (But why masking here?)
        .filter(|sym| sym.st_symtype() == STT_FUNC)
        .for_each(|sym| {
          let name = string_table
            .get(sym.st_name as usize)
            .unwrap_or_else(|_| panic!("fail to get name at st_name={}", sym.st_name));
          fn_sym_tab.insert(
            sym.st_value,
            FunctionSym { name: name.to_string(), info: sym.st_symtype() },
          );
        });
    } else {
      debug!("load_elf: symtab not found");
    };

    Ok((mem, fn_sym_tab))
  }

  fn write_mem(&mut self, addr: u32, alignment_bytes: u32, masks: &[bool], data: &[u8]) {
    // early return with strobe 0 write
    if !masks.iter().any(|&x| x) {
      return;
    }
    let size = data.len() as u32;
    debug!("write mem: size={size}, addr={addr:#x}");

    assert!(
      (addr % size == 0 || addr % alignment_bytes == 0) && size >= alignment_bytes,
      "unaligned write access addr={addr} size={size}bytes dlen={alignment_bytes}bytes"
    );

    masks.iter().enumerate().filter(|(_, &m)| m).for_each(|(i, _)| {
      self.mem[addr as usize + i] = data[i];
    });
  }

  pub fn axi_write(&mut self, addr: u32, strobe: &[bool], data: &[u8]) {
    // panic on misalign mask and data
    assert_eq!(
      strobe.len(),
      data.len(),
      "write_mem: strobe size is not equal to data size"
    );
    self.write_mem(addr, self.dlen / 8, strobe, data);
  }

  fn read_mem(&mut self, addr: u32, size: u32, alignment_bytes: u32) -> Vec<u8> {
    assert!(
      addr % size == 0 || addr % alignment_bytes == 0,
      "unaligned access addr={addr} size={size}bytes dlen={alignment_bytes}bytes"
    );
    let residue_addr = addr % alignment_bytes;
    let aligned_addr = addr - residue_addr;
    if size < alignment_bytes {
      // narrow
      (0..alignment_bytes)
        .map(|i| {
          let i_addr = aligned_addr + i;
          if addr <= i_addr && i_addr < addr + size {
            self.mem[i_addr as usize]
          } else {
            0
          }
        })
        .collect()
    } else {
      // normal
      (0..size).map(|i| self.mem[(addr + i) as usize]).collect()
    }
  }

  pub fn axi_read_instruction(&mut self, addr: u32, arsize: u64) -> AxiReadPayload {
    let size = 1 << arsize;
    assert!(size <= 4);
    let data = self.read_mem(addr, size, 4);
    let data_hex = hex::encode(&data);
    info!(
      "[{}] axi_read_indexed (addr={addr:#x}, size={size}, data={data_hex})",
      0
    );
    AxiReadPayload { data }
  }

  pub(crate) fn axi_read_load_store(&mut self, addr: u32, arsize: u64) -> AxiReadPayload {
    let size = 1 << arsize;
    let data = self.read_mem(addr, size, self.dlen / 8);
    let data_hex = hex::encode(&data);
    info!(
      "[{}] axi_read_high_bandwidth (addr={addr:#x}, size={size}, data={data_hex})",
      0
    );
    AxiReadPayload { data }
  }

  pub(crate) fn watchdog(&mut self) -> u8 {
    trace!("watchdog continue");
    WATCHDOG_CONTINUE
  }
}

#[cfg(test)]
mod test {
  use super::*;

  #[test]
  fn test_load_elf() {
    let _ = Simulator::load_elf(Path::new("./result/bin/codegen.vsseg4e8_v.elf")).unwrap();
    // TODO: verify address and bit
  }

  #[test]
  fn x86_should_fail() {
    let err = Simulator::load_elf(Path::new("/bin/cp")).unwrap_err();
    assert_eq!(format!("{}", err), "ELF is not in RISC-V")
  }
}
