use crate::bus::ShadowBus;
use crate::dpi::*;
use crate::OnlineArgs;
use crate::{get_t, EXIT_CODE, EXIT_POS};
use svdpi::SvScope;

use anyhow::Context;
use elf::{
  abi::{EM_RISCV, ET_EXEC, PT_LOAD, STT_FUNC},
  endian::LittleEndian,
  ElfStream,
};
use std::collections::HashMap;
use std::os::unix::fs::FileExt;
use std::{fs, path::Path};
use tracing::{debug, error, info, trace};

#[derive(Debug)]
#[allow(dead_code)]
pub struct FunctionSym {
  #[allow(dead_code)]
  pub(crate) name: String,
  #[allow(dead_code)]
  pub(crate) info: u8,
}
pub type FunctionSymTab = HashMap<u64, FunctionSym>;

pub(crate) struct Driver {
  // SvScope from t1rocket_cosim_init
  scope: SvScope,

  pub(crate) dlen: u32,
  pub(crate) e_entry: u64,

  timeout: u64,
  last_commit_cycle: u64,

  shadow_bus: ShadowBus,

  pub(crate) quit: bool,
  pub(crate) success: bool,
}

impl Driver {
  pub(crate) fn new(scope: SvScope, args: &OnlineArgs) -> Self {
    // pass e_entry to rocket
    let (e_entry, shadow_bus, _fn_sym_tab) =
      Self::load_elf(&args.elf_file).expect("fail creating simulator");

    Self {
      scope,

      dlen: args.dlen,
      e_entry,

      timeout: args.timeout,
      last_commit_cycle: 0,

      shadow_bus,

      quit: false,
      success: false,
    }
  }

  pub fn load_elf(path: &Path) -> anyhow::Result<(u64, ShadowBus, FunctionSymTab)> {
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

    debug!("ELF entry: 0x{:x}", elf.ehdr.e_entry);
    let mut mem = ShadowBus::new();
    let mut load_buffer = Vec::new();
    elf.segments().iter().filter(|phdr| phdr.p_type == PT_LOAD).for_each(|phdr| {
      let vaddr: usize = phdr.p_vaddr.try_into().expect("fail converting vaddr(u64) to usize");
      let filesz: usize = phdr.p_filesz.try_into().expect("fail converting p_filesz(u64) to usize");
      debug!(
        "Read loadable segments 0x{:x}..0x{:x} to memory 0x{:x}",
        phdr.p_offset,
        phdr.p_offset + filesz as u64,
        vaddr
      );

      // Load file start from offset into given mem slice
      // The `offset` of the read_at method is relative to the start of the file and thus independent from the current cursor.
      load_buffer.resize(filesz, 0u8);
      file.read_at(load_buffer.as_mut_slice(), phdr.p_offset).unwrap_or_else(|err| {
        panic!(
          "fail reading ELF into mem with vaddr={}, filesz={}, offset={}. Error detail: {}",
          vaddr, filesz, phdr.p_offset, err
        )
      });
      mem.load_mem_seg(vaddr, load_buffer.as_mut_slice());
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

    Ok((elf.ehdr.e_entry, mem, fn_sym_tab))
  }

  pub(crate) fn axi_read_high_bandwidth(&mut self, addr: u32, arsize: u64) -> AxiReadPayload {
    let size = 1 << arsize;
    let data = self.shadow_bus.read_mem_axi(addr, size, self.dlen / 8);
    let data_hex = hex::encode(&data);
    self.last_commit_cycle = get_t();
    trace!(
      "[{}] axi_read_high_bandwidth (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
    AxiReadPayload { data }
  }

  pub(crate) fn axi_write_high_bandwidth(
    &mut self,
    addr: u32,
    awsize: u64,
    strobe: &[bool],
    data: &[u8],
  ) {
    let size = 1 << awsize;
    self.shadow_bus.write_mem_axi(addr, size, self.dlen / 8, &strobe, data);
    let data_hex = hex::encode(data);
    self.last_commit_cycle = get_t();
    trace!(
      "[{}] axi_write_high_bandwidth (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
  }

  pub(crate) fn axi_read_high_outstanding(&mut self, addr: u32, arsize: u64) -> AxiReadPayload {
    let size = 1 << arsize;
    assert!(size <= 4);
    let data = self.shadow_bus.read_mem_axi(addr, size, 4);
    let data_hex = hex::encode(&data);
    self.last_commit_cycle = get_t();
    trace!(
      "[{}] axi_read_high_outstanding (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
    AxiReadPayload { data }
  }

  pub(crate) fn axi_write_high_outstanding(
    &mut self,
    addr: u32,
    awsize: u64,
    strobe: &[bool],
    data: &[u8],
  ) {
    let size = 1 << awsize;
    self.shadow_bus.write_mem_axi(addr, size, 4, strobe, data);
    let data_hex = hex::encode(data);
    self.last_commit_cycle = get_t();
    trace!(
      "[{}] axi_write_high_outstanding (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
  }

  pub(crate) fn axi_read_load_store(&mut self, addr: u32, arsize: u64) -> AxiReadPayload {
    let size = 1 << arsize;
    let bus_size = if size == 32 { 32 } else { 4 };
    let data = self.shadow_bus.read_mem_axi(addr, size, bus_size);
    let data_hex = hex::encode(&data);
    self.last_commit_cycle = get_t();
    trace!(
      "[{}] axi_read_load_store (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
    AxiReadPayload { data }
  }

  pub(crate) fn axi_write_load_store(
    &mut self,
    addr: u32,
    awsize: u64,
    strobe: &[bool],
    data: &[u8],
  ) {
    let size = 1 << awsize;
    let bus_size = if size == 32 { 32 } else { 4 };
    self.shadow_bus.write_mem_axi(addr, size, bus_size, strobe, data);
    let data_hex = hex::encode(data);
    self.last_commit_cycle = get_t();

    trace!(
      "[{}] axi_write_load_store (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );

    // check exit with code
    if addr == EXIT_POS {
      let exit_data_slice = data[..4].try_into().expect("slice with incorrect length");
      if u32::from_le_bytes(exit_data_slice) == EXIT_CODE {
        info!("driver is ready to quit");
        self.success = true;
        self.quit = true;
      }
    }
  }

  pub(crate) fn axi_read_instruction_fetch(&mut self, addr: u32, arsize: u64) -> AxiReadPayload {
    let size = 1 << arsize;
    let data = self.shadow_bus.read_mem_axi(addr, size, 32);
    let data_hex = hex::encode(&data);
    trace!(
      "[{}] axi_read_instruction_fetch (addr={addr:#x}, size={size}, data={data_hex})",
      get_t()
    );
    AxiReadPayload { data }
  }

  pub(crate) fn watchdog(&mut self) -> u8 {
    const WATCHDOG_CONTINUE: u8 = 0;
    const WATCHDOG_TIMEOUT: u8 = 1;

    let tick = get_t();
    if tick - self.last_commit_cycle > self.timeout {
      error!(
        "[{}] watchdog timeout (last_commit_cycle={})",
        get_t(),
        self.last_commit_cycle
      );
      WATCHDOG_TIMEOUT
    } else {
      trace!("[{}] watchdog continue", get_t());
      WATCHDOG_CONTINUE
    }
  }
}
