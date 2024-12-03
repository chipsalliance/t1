use crate::get_t;
use crate::interconnect::simctrl::ExitFlagRef;
use crate::interconnect::{create_emu_addrspace, AddressSpace};
use crate::OnlineArgs;
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
use tracing::{debug, error, trace};

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
  #[allow(unused)]
  scope: SvScope,

  pub(crate) dlen: u32,
  pub(crate) e_entry: u64,

  timeout: u64,
  last_commit_cycle: u64,

  addr_space: AddressSpace,

  pub(crate) exit_flag: ExitFlagRef,
}

impl Driver {
  pub(crate) fn new(scope: SvScope, args: &OnlineArgs) -> Self {
    let (mut addr_space, exit_flag) = create_emu_addrspace();
    // pass e_entry to rocket
    let (e_entry, _fn_sym_tab) =
      Self::load_elf(&args.elf_file, &mut addr_space).expect("fail creating simulator");

    Self {
      scope,

      dlen: args.dlen,
      e_entry,

      timeout: args.timeout,
      last_commit_cycle: 0,

      addr_space,

      exit_flag,
    }
  }

  // when error happens, `mem` may be left in an unspecified intermediate state
  pub fn load_elf(path: &Path, mem: &mut AddressSpace) -> anyhow::Result<(u64, FunctionSymTab)> {
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
      mem.write_mem(vaddr as u32, load_buffer.len() as u32, &load_buffer);
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

    Ok((elf.ehdr.e_entry, fn_sym_tab))
  }

  pub fn update_commit_cycle(&mut self) {
    self.last_commit_cycle = get_t();
  }

  // data_width: AXI width (count in bits)
  // return: Vec<u8> with len=bus_size*(arlen+1)
  // if size < bus_size, the result is padded due to AXI narrow transfer rules
  // if size < bus_size, arlen must be 0 (narrow burst is NOT supported)
  pub(crate) fn axi_read(
    &mut self,
    addr: u32,
    arsize: u32,
    arlen: u32,
    data_width: u32,
  ) -> Vec<u8> {
    let bus_size = data_width / 8;
    let size = 1 << arsize;

    assert!(
      addr % size == 0 && bus_size % size == 0,
      "unaligned read addr={addr:#x} size={size}B dlen={bus_size}B"
    );

    assert!(
      !(size < bus_size && arlen > 0),
      "narrow burst not supported, axsize={arsize}, axlen={arlen}, data_width={data_width}"
    );
    let transaction_size = bus_size * (arlen + 1);

    let mut data = vec![0; transaction_size as usize];
    if size < bus_size {
      assert_eq!(arlen, 0);
      let start = (addr % bus_size) as usize;
      let end = start + (size as usize);
      self.addr_space.read_mem(addr, size, &mut data[start..end]);
    } else {
      self.addr_space.read_mem(addr, transaction_size, &mut data);
    }
    data
  }

  // data_width: AXI width (count in bits)
  pub(crate) fn axi_write(
    &mut self,
    addr: u32,
    awsize: u32,
    data_width: u32,
    strobe: &[bool],
    data: &[u8],
  ) {
    let bus_size = data_width / 8;
    let size = 1 << awsize;

    assert!(
      addr % size == 0 && bus_size % size == 0,
      "unaligned write addr={addr:#x} size={size}B dlen={bus_size}B"
    );

    if size < bus_size {
      let start = (addr % bus_size) as usize;
      let end = start + (size as usize);

      // AXI spec says strobe outsize start..end shall be inactive, check it
      assert!(strobe.iter().copied().enumerate().all(|(idx, x)| !x || (start <= idx && idx < end)),
        "AXI write ill-formed [T={}] data_width={data_width}, addr=0x{addr:08x}, awsize={awsize}, strobe={strobe:?}",
        get_t(),
      );

      self.addr_space.write_mem_masked(addr, size, &data[start..end], &strobe[start..end]);
    } else {
      self.addr_space.write_mem_masked(addr, size, data, strobe);
    }
  }

  pub(crate) fn watchdog(&mut self) -> u8 {
    const WATCHDOG_CONTINUE: u8 = 0;
    const WATCHDOG_TIMEOUT: u8 = 1;
    const WATCHDOG_QUIT: u8 = 255;

    let tick = get_t();

    if self.exit_flag.is_finish() {
      trace!("[{tick}] watchdog quit");
      return WATCHDOG_QUIT;
    }

    if tick - self.last_commit_cycle > self.timeout {
      error!(
        "[{tick}] watchdog timeout (last_commit_cycle={})",
        self.last_commit_cycle
      );
      return WATCHDOG_TIMEOUT;
    }

    trace!("[{tick}] watchdog continue");
    WATCHDOG_CONTINUE
  }
}
