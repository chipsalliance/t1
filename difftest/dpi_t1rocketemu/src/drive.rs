use crate::get_t;
use crate::interconnect::simctrl::ExitFlagRef;
use crate::interconnect::{create_emu_addrspace_with_initmem, AddressSpace, MemReqPayload, SRAM_BASE, SRAM_SIZE};
use dpi_common::util::MetaConfig;
use svdpi::SvScope;

use anyhow::Context;
use elf::{
  abi::{EM_RISCV, ET_EXEC, PT_LOAD, STT_FUNC},
  endian::LittleEndian,
  ElfStream,
};
use std::collections::{HashMap, VecDeque};
use std::os::unix::fs::FileExt;
use std::path::PathBuf;
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

pub struct OnlineArgs {
  /// Path to the ELF file
  pub elf_file: String,

  /// dlen config
  pub dlen: u32,

  /// vlen config
  pub vlen: u32,

  /// ISA config
  pub spike_isa: String,

  /// DRAMsim3 configuartion (if any)
  pub dramsim3_cfg: Option<PathBuf>,
}

pub struct IncompleteWrite {
  addr: u64,
  bursts: usize,
  width: usize, // In bytes
  user: u64,

  sent: bool, // Sent to memory system
  done: bool, // Address Space has finished this write

  data: Vec<u8>,
  strb: Vec<bool>,

  // Used for transfers
  bus_size: usize,
}

impl IncompleteWrite {
  pub fn new(awaddr: u64, awlen: u64, awsize: u64, awuser: u64, data_width: u64) -> IncompleteWrite {
    let bus_size = data_width / 8;
    let size = 1 << awsize;

    assert!(
      awaddr % size == 0 && bus_size % size == 0,
      "unaligned read addr={awaddr:#x} size={size}B dlen={bus_size}B"
    );

    assert!(
      !(size < bus_size && awlen > 0),
      "narrow burst not supported, axsize={awsize}, axlen={awlen}, data_width={data_width}"
    );
    let tsize = (size * (awlen + 1)) as usize;
    let data = Vec::with_capacity(tsize);
    let strb = Vec::with_capacity(tsize);

    IncompleteWrite {
      addr: awaddr,
      bursts: awlen as usize + 1,
      width: size as usize,
      user: awuser,

      sent: false,
      done: false,
      data, strb,
      bus_size: bus_size as usize,
    }
  }

  pub fn push(&mut self, wdata: &[u8], wstrb: impl Iterator<Item = bool>, wlast: bool, data_width: u64) {
    let next_addr = self.addr as usize + self.data.len();
    let wire_offset = next_addr % self.bus_size;
    assert!(wire_offset as usize + self.width <= self.bus_size, "Sanity check for data width: IncompleteWrite::push");
    assert!(data_width as usize == self.bus_size * 8, "Mismatch data width across DPI calls");
    self.data.extend(&wdata[wire_offset as usize .. (wire_offset as usize + self.width)]);
    self.strb.extend(wstrb.skip(wire_offset).take(self.width));
    assert_eq!(self.data.len(), self.strb.len());
    assert!(self.data.len() <= (self.bursts * self.width) as usize);
    if self.ready() {
      assert!(wlast);
    }
  }

  /// Ready to send to mem
  pub fn ready(&self) -> bool {
    self.data.len() == (self.bursts * self.width) as usize
  }
}

pub struct IncompleteRead {
  addr: u64,
  bursts: usize,
  width: usize,
  user: u64,

  sent: bool, // Sent to memory
  data: Option<VecDeque<u8>>,

  // Used for transfers
  bus_size: u64,
}

pub(crate) struct Driver {
  // SvScope from t1rocket_cosim_init
  #[allow(unused)]
  scope: SvScope,

  pub(crate) meta: MetaConfig,

  pub(crate) dlen: u32,
  pub(crate) e_entry: u64,

  timeout: u64,
  last_commit_cycle: u64,

  addr_space: AddressSpace,

  pub(crate) exit_flag: ExitFlagRef,

  /// (channel_id, id) -> data
  pub(crate) incomplete_writes: HashMap<(u64, u64), VecDeque<IncompleteWrite>>,
  pub(crate) incomplete_reads: HashMap<(u64, u64), VecDeque<IncompleteRead>>,
}

impl Driver {
  pub(crate) fn new(scope: SvScope, args: &OnlineArgs) -> Self {
    let mut initmem = vec![0; SRAM_SIZE as usize];
    let (e_entry, _fn_sym_tab) =
      Self::load_elf(Path::new(&args.elf_file), &mut initmem).expect("fail creating simulator");

    let (mut addr_space, exit_flag) = create_emu_addrspace_with_initmem(initmem);
    // pass e_entry to rocket

    Self {
      scope,

      meta: MetaConfig {
        vlen: args.vlen,
        dlen: args.dlen,
        isa: args.spike_isa.clone(),
        elf_file: Some(args.elf_file.clone()),
      },

      dlen: args.dlen,
      e_entry,

      timeout: 0,
      last_commit_cycle: 0,

      addr_space,

      exit_flag,

      incomplete_reads: HashMap::new(),
      incomplete_writes: HashMap::new(),
    }
  }

  // when error happens, `mem` may be left in an unspecified intermediate state
  pub fn load_elf(path: &Path, mem: &mut [u8]) -> anyhow::Result<(u64, FunctionSymTab)> {
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
      let dest = &mut mem[(vaddr - SRAM_BASE as usize)..(vaddr - SRAM_BASE as usize + load_buffer.len())];
      dest.copy_from_slice(&load_buffer);
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

  pub fn tick(&mut self) {
    // Allow sending multiple
    for ((cid, id), fifo) in self.incomplete_writes.iter_mut() {
      // Always handled in-order, find first pending
      let w = fifo.iter_mut().find(|w| !w.sent);
      if w.as_ref().is_none_or(|w| !w.ready()) { continue; }
      let w = w.unwrap();

      // [16 bit W = 0][ 16 bit cid ][ 32 bit id ]
      let mapped_id = cid << 32 | id;
      let payload = MemReqPayload::Write(w.data.as_slice(), Some(w.strb.as_slice()));
      if self.addr_space.req(*id, w.addr as u32, (w.bursts * w.width) as u32, payload) {
        w.sent = true;
      }
    };

    for ((cid, id), fifo) in self.incomplete_reads.iter_mut() {
      let r = fifo.iter_mut().find(|w| !w.sent);
      if r.is_none() { continue; }
      let r = r.unwrap();

      // [16 bit W = 0][ 16 bit cid ][ 32 bit id ]
      let mapped_id = 1 << 48 | cid << 32 | id;
      let payload = MemReqPayload::Read;
      if self.addr_space.req(*id, r.addr as u32, (r.bursts * r.width) as u32, payload) {
        r.sent = true;
      }
    };

    self.addr_space.tick();
    
    while let Some((mapped_id, payload)) = self.addr_space.resp() {
      let is_write = mapped_id >> 48 == 0;
      let cid = mapped_id >> 32 & 0xFFFF;
      let id = mapped_id & 0xFFFFFFFF;
      match payload {
        crate::interconnect::MemRespPayload::ReadBuffered(buf) => {
          assert!(!is_write);
          let fifo = self.incomplete_reads.get_mut(&(cid, id)).expect("Returned read has no corresponding pending data");
          let r = fifo.iter_mut().find(|r| r.data.is_none());
          if r.as_ref().is_none_or(|r| !r.sent) { continue; }
          r.unwrap().data = Some(VecDeque::from(buf.to_owned()));
        }
        crate::interconnect::MemRespPayload::ReadRegister(buf) => {
          assert!(!is_write);
          let fifo  = self.incomplete_reads.get_mut(&(cid, id)).expect("Returned read has no corresponding pending data");
          let r = fifo.iter_mut().find(|r| r.data.is_none());
          if r.as_ref().is_none_or(|r| !r.sent) { continue; }
          r.unwrap().data = Some(VecDeque::from(buf));
        }
        crate::interconnect::MemRespPayload::WriteAck => {
          assert!(is_write);
          let fifo  = self.incomplete_writes.get_mut(&(cid, id)).expect("Returned write has no corresponding pending data");
          let w = fifo.iter_mut().find(|w| !w.done);
          if w.as_ref().is_none_or(|w| !w.sent) { continue; }
          w.unwrap().done = true;
        }
      }
    }
  }

  /*/
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
  */

  pub(crate) fn set_timeout(&mut self, timeout: u64) {
    self.timeout = timeout;
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

    if self.timeout > 0 && tick - self.last_commit_cycle > self.timeout {
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
