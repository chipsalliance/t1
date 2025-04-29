use crate::get_t;
use crate::interconnect::simctrl::ExitFlagRef;
use crate::interconnect::{
  AddressSpace, BusError, MemReqPayload, MemRespPayload, RAM_BASE, RAM_SIZE, RegularMemory,
  create_emu_addrspace_with_mem,
};
use dpi_common::util::MetaConfig;
use svdpi::SvScope;

use anyhow::Context;
use elf::{
  ElfStream,
  abi::{EM_RISCV, ET_EXEC, PT_LOAD, STT_FUNC},
  endian::LittleEndian,
};
use std::collections::{BTreeMap, HashMap, VecDeque};
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

pub struct OnlineArgs<'a> {
  /// Path to the ELF file
  pub elf_file: String,

  /// dlen config
  pub dlen: u32,

  /// vlen config
  pub vlen: u32,

  /// ISA config
  pub spike_isa: String,

  /// DRAMsim3 configuartion and run-path (if any)
  pub dramsim3: Option<(&'a Path, &'a Path)>,
}

/// An incomplete memory write
/// Keeps track of both the data filling and the request into
/// AddressSpace itself
#[derive(Debug)]
pub struct IncompleteWrite {
  id: u64,
  addr: u64,
  bursts: usize,
  width: usize, // In bytes
  user: u64,

  /// Is this request already sent to the memory?
  sent: bool,
  /// Is this request processed by the memory?
  done: Option<Result<(), BusError>>,

  data: Vec<u8>,
  strb: Vec<bool>,

  // Used for transfers
  bus_size: usize,
}

impl IncompleteWrite {
  pub fn new(
    awid: u64,
    awaddr: u64,
    awlen: u64,
    awsize: u64,
    awuser: u64,
    data_width: u64,
  ) -> IncompleteWrite {
    let bus_size = data_width / 8;
    let size = 1 << awsize;

    assert!(
      awaddr % size == 0 && bus_size % size == 0,
      "SIM ERROR: unaligned write addr={awaddr:#x} size={size}B dlen={bus_size}B"
    );

    // TODO: narrow burst is actually supported, but not tested
    assert!(
      !(size < bus_size && awlen > 0),
      "SIM ERROR: narrow burst not supported, axsize={awsize}, axlen={awlen}, data_width={data_width}"
    );

    assert!(
      awaddr % 4096 + size * (awlen + 1) <= 4096,
      "SIM ERROR: axi write transaction across 4KiB boundary, addr={awaddr:#x}, awsize={awsize}, awlen={awlen}",
    );

    let tsize = (size * (awlen + 1)) as usize;
    let data = Vec::with_capacity(tsize);
    let strb = Vec::with_capacity(tsize);

    IncompleteWrite {
      id: awid,
      addr: awaddr,
      bursts: awlen as usize + 1,
      width: size as usize,
      user: awuser,

      sent: false,
      done: None,
      data,
      strb,
      bus_size: bus_size as usize,
    }
  }

  /// Add an AXI W channel beat
  pub fn push(&mut self, wdata: &[u8], wstrb: impl Iterator<Item = bool>, wlast: bool) {
    assert_eq!(
      wdata.len(),
      self.bus_size,
      "Mismatch data width across DPI calls"
    );

    let next_addr = self.addr as usize + self.data.len();
    let wire_offset = next_addr % self.bus_size;
    assert!(
      wire_offset as usize + self.width <= self.bus_size,
      "Sanity check for data width: IncompleteWrite::push"
    );

    self.data.extend(&wdata[wire_offset..wire_offset + self.width]);
    self.strb.extend(wstrb.skip(wire_offset).take(self.width));

    assert_eq!(self.data.len(), self.strb.len());
    assert!(self.data.len() <= (self.bursts * self.width) as usize);

    assert_eq!(wlast, self.ready());
  }

  /// Ready to send to mem
  pub fn ready(&self) -> bool {
    self.data.len() == (self.bursts * self.width) as usize
  }

  pub fn complete_ok(&mut self) {
    assert!(self.done.is_none(), "Write transaction is already done");
    self.done = Some(Ok(()));
  }

  pub fn complete_with_error(&mut self, e: BusError) {
    assert!(self.done.is_none(), "Write transaction is already done");
    self.done = Some(Err(e));
  }

  pub fn done(&self) -> bool {
    self.done.is_some()
  }

  pub fn resp(&self) -> Result<(), BusError> {
    self.done.as_ref().expect("Write transanction is not done").clone()
  }

  pub fn id(&self) -> u64 {
    self.id
  }

  pub fn user(&self) -> u64 {
    self.user
  }
}

/// An incomplete memory read
/// Keeps track of both the data draining and the request into
/// AddressSpace itself
#[derive(Debug)]
pub struct IncompleteRead {
  addr: u64,
  bursts: usize,
  width: usize,
  user: u64,

  /// Is this request sent to memory?
  sent: bool,
  /// The number of bytes already returned to the RTL
  returned: usize,
  /// The fetched data. None if the response has not arrived yet
  data: Option<Result<Vec<u8>, BusError>>,

  // Used for transfers
  bus_size: usize,
}

impl IncompleteRead {
  pub fn new(araddr: u64, arlen: u64, arsize: u64, aruser: u64, data_width: u64) -> IncompleteRead {
    let bus_size = data_width / 8;
    let size = 1 << arsize;

    assert!(
      araddr % size == 0 && bus_size % size == 0,
      "SIM ERROR: unaligned read addr={araddr:#x} size={size}B dlen={bus_size}B"
    );

    // TODO: narrow burst is actually supported, but not tested
    assert!(
      !(size < bus_size && arlen > 0),
      "SIM ERROR: narrow burst not supported, axsize={arsize}, axlen={arlen}, data_width={data_width}"
    );

    assert!(
      araddr % 4096 + size * (arlen + 1) <= 4096,
      "SIM ERROR: axi read transaction across 4KiB boundary, addr={araddr:#x}, arsize={arsize}, arlen={arlen}",
    );

    IncompleteRead {
      addr: araddr,
      bursts: arlen as usize + 1,
      width: size as usize,
      user: aruser,

      sent: false,
      returned: 0,
      data: None,

      bus_size: bus_size as usize,
    }
  }

  /// Drain one beat into the AXI R channel
  ///
  /// Returns true if this is the last beat in the response (rlast)
  pub fn pop(&mut self, rdata_buf: &mut [u8]) -> (Result<(), BusError>, bool) {
    assert_eq!(
      rdata_buf.len(),
      self.bus_size,
      "Mismatch data width across DPI calls"
    );

    assert!(
      self.returned < self.bursts * self.width,
      "All data already taken by RTL"
    );

    let data = self
      .data
      .as_ref()
      .expect("IncompleteRead::pop called on request that hasn't gotten its data!");

    let resp = match data {
      Err(e) => Err(e.clone()),
      Ok(data) => {
        // Find in-line offset
        let result_offset = (self.addr as usize + self.returned) % self.bus_size;

        let dst = &mut rdata_buf[result_offset..(result_offset + self.width)];
        let src = &data[self.returned..(self.returned + self.width)];
        dst.copy_from_slice(src);
        Ok(())
      }
    };

    self.returned += self.width;
    assert!(self.returned <= self.bursts * self.width);

    let last = self.returned == self.bursts * self.width;
    (resp, last)
  }

  pub fn complete_with_data(&mut self, data: Vec<u8>) {
    assert!(self.data.is_none(), "Read transaction is already done");
    assert_eq!(
      data.len(),
      self.bursts * self.width,
      "Transaction data width mismatch"
    );

    self.data = Some(Ok(data));
  }

  pub fn complete_with_error(&mut self, e: BusError) {
    assert!(self.data.is_none(), "Read transaction is already done");
    self.data = Some(Err(e));
  }

  pub fn done(&self) -> bool {
    self.data.is_some()
  }

  pub fn user(&self) -> u64 {
    self.user
  }
}

pub(crate) struct Driver {
  // SvScope from t1rocket_cosim_init
  #[allow(unused)]
  scope: SvScope,

  pub(crate) meta: MetaConfig,

  pub(crate) dlen: u32,
  pub(crate) e_entry: u64,

  next_tick: u64,
  timeout: u64,
  last_commit_cycle: u64,

  addr_space: AddressSpace,

  pub(crate) exit_flag: ExitFlagRef,

  /// (channel_id, id) -> data
  pub(crate) incomplete_writes: BTreeMap<u64, VecDeque<IncompleteWrite>>,
  pub(crate) incomplete_reads: BTreeMap<(u64, u64), VecDeque<IncompleteRead>>,
}

impl Driver {
  pub(crate) fn new(scope: SvScope, args: &OnlineArgs<'_>) -> Self {
    let mut initmem = vec![0; RAM_SIZE as usize];
    let (e_entry, _fn_sym_tab) =
      Self::load_elf(Path::new(&args.elf_file), &mut initmem).expect("fail creating simulator");

    let (addr_space, exit_flag) = match args.dramsim3 {
      Some((cfg_path, run_path)) => {
        let mem = RegularMemory::with_content_and_model(initmem, cfg_path, run_path);
        create_emu_addrspace_with_mem(mem)
      }
      None => {
        let mem = RegularMemory::with_content(initmem);
        create_emu_addrspace_with_mem(mem)
      }
    };
    // pass e_entry to rocket

    Self {
      scope,

      meta: MetaConfig {
        vlen: args.vlen,
        dlen: args.dlen,
        isa: args.spike_isa.clone(),
        elf_file: Some(args.elf_file.clone()),
        dramsim3_enabled: args.dramsim3.is_some(),
      },

      dlen: args.dlen,
      e_entry,

      next_tick: 0,
      timeout: 0,
      last_commit_cycle: 0,

      addr_space,

      exit_flag,

      incomplete_reads: BTreeMap::new(),
      incomplete_writes: BTreeMap::new(),
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
      let dest =
        &mut mem[(vaddr - RAM_BASE as usize)..(vaddr - RAM_BASE as usize + load_buffer.len())];
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

  /// Ticking the peripherals
  pub fn tick(&mut self) {
    // This tick happens on the posedge of each clock
    // Also it may be called multiple time because of multiple slaves
    // so here we check if we have already ticked this cycle.
    let desired_tick = get_t();
    if self.next_tick != 0 && desired_tick > self.next_tick {
      panic!("Skipped a tick: {} -> {}", self.next_tick, desired_tick);
    }

    if self.next_tick > desired_tick {
      assert!(self.next_tick == desired_tick + 1);
      return;
    }
    self.next_tick = desired_tick + 1;

    // Then this function handles the real ticking, which contains three steps:
    // 1. Send all requests
    // 2. Ticking the AddressSpace
    // 3. Poll the responses
    // This way, memory accesses can be returned in the next cycle for peripherals with no latency

    // Allow sending multiple
    for (cid, fifo) in self.incomplete_writes.iter_mut() {
      // Always handled in-order, find first pending
      let w = fifo.iter_mut().find(|w| !w.sent);
      if w.as_ref().is_none_or(|w| !w.ready()) {
        continue;
      }
      let w = w.unwrap();

      // [16 bit W = 0][ 16 bit cid ][ 32 bit id ]
      let mapped_id = cid << 32 | w.id;
      let payload = MemReqPayload::Write(w.data.as_slice(), Some(w.strb.as_slice()));

      debug!(
        "[{}] Committing write: {:?} -> 0x{:x}",
        get_t(),
        payload,
        w.addr
      );
      if self.addr_space.req(
        mapped_id,
        w.addr as u32,
        (w.bursts * w.width) as u32,
        payload,
      ) {
        w.sent = true;
      }
    }

    for ((cid, id), fifo) in self.incomplete_reads.iter_mut() {
      let r = fifo.iter_mut().find(|w| !w.sent);
      if r.is_none() {
        continue;
      }
      let r = r.unwrap();

      // [16 bit W = 0][ 16 bit cid ][ 32 bit id ]
      let mapped_id = 1 << 48 | cid << 32 | id;
      let payload = MemReqPayload::Read;
      if self.addr_space.req(
        mapped_id,
        r.addr as u32,
        (r.bursts * r.width) as u32,
        payload,
      ) {
        r.sent = true;
      }
    }

    self.addr_space.tick();

    while let Some((mapped_id, payload)) = self.addr_space.resp() {
      let is_write = mapped_id >> 48 == 0;
      let cid = mapped_id >> 32 & 0xFFFF;
      let id = mapped_id & 0xFFFFFFFF;

      if !is_write {
        let r = self
          .incomplete_reads
          .get_mut(&(cid, id))
          .and_then(|f| f.iter_mut().find(|r| !r.done()))
          .expect("Returned read has no corresponding pending data");
        assert!(r.sent);
        match payload {
          MemRespPayload::ReadBuffered(buf) => r.complete_with_data(buf.to_owned()),
          MemRespPayload::ReadRegister(buf) => r.complete_with_data(Vec::from(buf)),
          MemRespPayload::ReadError(e) => r.complete_with_error(e),
          MemRespPayload::WriteAck | MemRespPayload::WriteError(_) => unreachable!(),
        }
      } else {
        let w = self
          .incomplete_writes
          .get_mut(&cid)
          .and_then(|f| f.iter_mut().find(|w| w.id == id && !w.done()))
          .expect("Returned write has no corresponding pending data");
        match payload {
          MemRespPayload::ReadBuffered(_)
          | MemRespPayload::ReadRegister(_)
          | MemRespPayload::ReadError(_) => unreachable!(),
          MemRespPayload::WriteAck => w.complete_ok(),
          MemRespPayload::WriteError(e) => w.complete_with_error(e),
        }
      }
    }
  }

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
