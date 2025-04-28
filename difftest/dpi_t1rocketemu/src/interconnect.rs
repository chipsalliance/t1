use std::{
  any::Any,
  cell::Cell,
  collections::{BinaryHeap, HashSet, VecDeque},
  path::Path,
  rc::Rc,
};
use tracing::debug;

use framebuffer::FrameBuffer;
use simctrl::{ExitFlagRef, SimCtrl};

pub mod framebuffer;
pub mod simctrl;

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub struct AddrInfo {
  pub offset: u32,
  pub len: u32,
}

impl AddrInfo {
  pub fn as_range(self) -> std::ops::Range<usize> {
    self.offset as usize..(self.offset + self.len) as usize
  }
}

/// Payload of a memory request
#[derive(Debug)]
pub enum MemReqPayload<'a> {
  Read,
  Write(&'a [u8], Option<&'a [bool]>),
}

/// A memory request
///
/// The device should keep the ordering of requests of the same ID
/// Caller is reponsible to ensure the following conditions hold:
///   addr.len > 0
///   addr.len == data.len()
///   addr.len == mask.len() (if mask present)
/// However, since all use sites (Device impls) of this struct are safe,
/// even if contracts violate, implementions must not break memory safety,
#[derive(Debug)]
pub struct MemReq<'a> {
  pub id: u64,
  pub payload: MemReqPayload<'a>,
  pub addr: AddrInfo,
}

impl<'a> MemReqPayload<'a> {
  fn is_write(&self) -> bool {
    match self {
      MemReqPayload::Read => false,
      MemReqPayload::Write(_, _) => true,
    }
  }
}

/// Payload of memory response
///
/// Here we distinguish a fixed-sized MMIO register response
/// from a arbitrarily-sized bulk memory response to simplify
/// the implementation of RegDevice, since fixed-sized arrays
/// are not borrowed
#[derive(Debug)]
pub enum MemRespPayload<'a> {
  ReadBuffered(&'a [u8]),
  ReadRegister([u8; 4]),
  WriteAck,
}

/// A memory response
///
/// IDs correlates the response with the request
#[derive(Debug)]
pub struct MemResp<'a> {
  pub id: u64,
  pub payload: MemRespPayload<'a>,
}

pub trait Device: Any + Send {
  fn req(&mut self, req: MemReq<'_>) -> bool;
  fn resp(&mut self) -> Option<MemResp<'_>>;
  fn tick(&mut self);
}

impl<T: Device> DeviceExt for T {}
pub trait DeviceExt: Device + Sized {
  fn with_addr(self, addr: u32, size: u32) -> DeviceEntry {
    DeviceEntry {
      base_and_size: (addr, size),
      device: Box::new(self),
    }
  }
}

// Represents a MMIO devices consists of 4-byte 'registers'.
// Support only 4-byte aligned read/write, not support write mask
// `offset` is offset in bytes from base address, guaranteed to be multiple of 4.
// I choose offset in byte since it's usaully better aligned with document
pub trait RegDevice {
  // Panic for bus error
  fn reg_read(&mut self, offset: u32) -> u32;

  // Panic for bus error
  fn reg_write(&mut self, offset: u32, value: u32);
}

/// Wrapping a reg device to implement the device trait
///
/// We need a wrapper because we have to keep track of pending
/// MemResps. This is in turn due to the separation of request pushing
/// and response polling.
struct WrappedRegDevice<RD: RegDevice> {
  device: RD,
  pending: Option<MemResp<'static>>,
}

impl<RD: RegDevice> WrappedRegDevice<RD> {
  fn new(device: RD) -> Self {
    Self { device, pending: None }
  }
}

impl<T: RegDevice + Send + 'static> Device for WrappedRegDevice<T> {
  fn req(&mut self, req: MemReq<'_>) -> bool {
    // allows only 4-byte aligned access
    assert_eq!(4, req.addr.len);
    assert!(req.addr.offset % 4 == 0);

    if self.pending.is_some() {
      return false;
    }

    match req.payload {
      MemReqPayload::Read => {
        let value = self.device.reg_read(req.addr.offset);
        self.pending = Some(MemResp {
          id: req.id,
          payload: MemRespPayload::ReadRegister(u32::to_le_bytes(value)),
        });
      }
      MemReqPayload::Write(data, mask) => {
        if let Some(m) = mask {
          assert!(m.iter().all(|&x| x));
        }

        let value = u32::from_le_bytes(data.try_into().unwrap());
        self.device.reg_write(req.addr.offset, value);
        self.pending = Some(MemResp { id: req.id, payload: MemRespPayload::WriteAck });
      }
    }
    true
  }

  fn resp(&mut self) -> Option<MemResp<'_>> {
    self.pending.take()
  }

  fn tick(&mut self) {} // This is a no-op for Reg devices
}

/// An abstract memory request (identifier) for memory models
#[derive(Debug)]
pub struct MemIdent {
  id: u64,
  req: AddrInfo,
  is_write: bool,
}

impl Into<MemIdent> for InflightMem {
  fn into(self) -> MemIdent {
    MemIdent {
      id: self.id,
      req: self.req,
      is_write: self.is_write,
    }
  }
}

/// The memory model interface
///
/// A memory model simulates the latency of a memory device,
/// but don't keep track of its content.
pub trait MemoryModel {
  fn push(&mut self, req: MemIdent);
  fn pop(&mut self) -> Option<MemIdent>;
  fn tick(&mut self);
}

/// AddrSet repersents a subset of a specific memory slice, where
/// each element (line) have uniform width
///
/// A memory request with arbitrary length may need to be segmented
/// into multiple smaller memory requests. AddrSets are used to individually
/// keep track of these requests' progress.
#[derive(Clone, Debug)]
pub struct AddrSet {
  pub base: u32,
  pub line_size: u16,
  pub set: u16,
}

impl AddrSet {
  /// Create an addr set based on the unaligned base, unaligned length, and the line size
  ///
  /// the returned set contains all elements initially.
  pub fn new(unaligned_base: u32, len: u32, line_size: u32) -> AddrSet {
    let base = (unaligned_base / line_size) * line_size;
    let prepend = unaligned_base - base;
    let line_num = ((len + prepend) + line_size - 1) / line_size;
    assert!(
      line_num <= 16,
      "line number {} > 16 not supported!",
      line_num
    );
    let set = if line_num == 16 {
      u16::MAX
    } else {
      ((1 << line_num) - 1) as u16
    };

    AddrSet { base, line_size: line_size as u16, set }
  }

  /// Returns if this set is empty
  pub fn empty(&self) -> bool {
    self.set == 0
  }

  /// Remove a element based on its index
  ///
  /// Returns if the element is previously in the set
  pub fn remove(&mut self, idx: u32) -> bool {
    if idx >= 16 {
      return false;
    }
    let currently_set = self.set & (1u16 << idx) != 0;
    self.set &= !(1u16 << idx);
    currently_set
  }

  /// Remove a element based on its address
  /// The address has to reside within the range, and is aligned
  /// to the line size.
  ///
  /// Returns if the element is previously in the set
  pub fn remove_addr(&mut self, addr: u32) -> bool {
    if addr < self.base {
      return false;
    }
    let diff = addr - self.base;
    if diff % self.line_size as u32 != 0 {
      return false;
    }
    let idx = diff / self.line_size as u32;
    self.remove(idx)
  }
}

impl Iterator for AddrSet {
  type Item = (u32, u32); // (addr, offset)

  fn next(&mut self) -> Option<Self::Item> {
    if self.set == 0 {
      return None;
    }
    let last_one = self.set.trailing_zeros();
    let addr = self.base + self.line_size as u32 * last_one;
    self.remove(last_one);
    return Some((addr, last_one));
  }
}

/// Book-keeping structure for DRAMsim models
#[derive(Debug)]
struct InflightMem {
  id: u64,
  req: AddrInfo,
  is_write: bool,

  /// Un-sent requests
  req_wait: AddrSet,
  /// Un-acked responses
  resp_wait: AddrSet,
}

impl InflightMem {
  fn from_ident(ident: MemIdent, line_size: u32) -> Self {
    let set = AddrSet::new(ident.req.offset, ident.req.len, line_size);
    Self {
      id: ident.id,
      req: ident.req,
      is_write: ident.is_write,

      req_wait: set.clone(),
      resp_wait: set,
    }
  }

  /// Returns if all sub-requests are sent to the memory model
  fn sent(&self) -> bool {
    self.req_wait.empty()
  }

  /// Returns if all sub-responses are received
  fn done(&self) -> bool {
    self.resp_wait.empty()
  }
}

/// The DRAM memory model based on DRAMsim3
///
/// The ticking speed of this model should be the system clock speed,
/// as it keeps track of the DRAM clock tick internally and compensates
/// for the clock speed difference.
pub struct DRAMModel {
  sys: dramsim3::MemorySystem,
  inflights: Vec<InflightMem>,
  // TODO: implement cache
  _cached: BinaryHeap<u32>,
  dram_tick: usize,
  sys_tick: usize,
}

// FIXME: impl Send in upstream MemorySystem
unsafe impl Send for DRAMModel {}

impl DRAMModel {
  fn new(ds_cfg: &Path, ds_path: &Path) -> Self {
    let chunk_size: Rc<Cell<u32>> = Rc::new(Cell::new(0));
    let chunk_size_clone = chunk_size.clone();
    let sys =
      dramsim3::MemorySystem::new(ds_cfg, ds_path).expect("dramsim3 MemorySystem creation failed");
    let ret = DRAMModel {
      sys,
      inflights: vec![],
      _cached: BinaryHeap::new(),
      dram_tick: 0,
      sys_tick: 0,
    };

    chunk_size_clone.set(ret.req_size());
    ret
  }

  /// Size of each request to DRAM, in bytes
  fn req_size(&self) -> u32 {
    (self.sys.burst_length() * self.sys.bus_bits() / 8) as u32
  }
}

impl MemoryModel for DRAMModel {
  fn push(&mut self, req: MemIdent) {
    // TODO: done if in cache
    debug!("DRAM Pushing: {:x}, size = {}", req.req.offset, req.req.len);
    self.inflights.push(InflightMem::from_ident(req, self.req_size()));
  }

  fn pop(&mut self) -> Option<MemIdent> {
    // Take exact one of the inflight request that are fully done
    // Also ensure no preceding requests that has conflicting ID
    let mut blocked = HashSet::with_capacity(32);
    let inf = &mut self.inflights;
    for i in 0..inf.len() {
      if inf[i].done() && !blocked.contains(&inf[i].id) {
        return Some(inf.remove(i).into());
      } else {
        blocked.insert(inf[i].id);
      }
    }
    None
  }

  fn tick(&mut self) {
    for inflight in &mut self.inflights {
      if inflight.sent() {
        continue;
      }
      for (addr, idx) in inflight.req_wait.clone() {
        if !self.sys.can_add(addr as u64, inflight.is_write) {
          continue;
        }
        debug!(
          "DRAM Memory request: {:x}, write={}, id=0x{:x}",
          addr, inflight.is_write, inflight.id
        );
        self.sys.add(addr as u64, inflight.is_write);
        inflight.req_wait.remove(idx);
      }
    }

    self.sys_tick += 1;
    let dram_tck = self.sys.tck(); // In ns
    let sys_tck = crate::get_sys_tck();
    while self.sys_tick as f64 * sys_tck > self.dram_tick as f64 * dram_tck {
      let inflights = &mut self.inflights;
      self.sys.tick(|addr, is_write| {
        debug!("DRAM Memory response: {:x}, write={}", addr, is_write);
        for req in &mut *inflights {
          if req.is_write == is_write && req.resp_wait.remove_addr(addr as u32) {
            debug!("Found req: id={:x}", req.id);
            return;
          }
        }
        debug!("All requests: {:#?}", inflights);
        panic!("Unexpected memory response!");
      });
      self.dram_tick += 1;
    }
  }
}

/// A trivial memory model, where all requests are immediately resolved
/// and served in the FIFO order
#[derive(Default)]
pub struct TrivialModel {
  holding: VecDeque<MemIdent>,
}

impl MemoryModel for TrivialModel {
  fn push(&mut self, req: MemIdent) {
    self.holding.push_back(req);
  }
  fn pop(&mut self) -> Option<MemIdent> {
    self.holding.pop_front()
  }
  fn tick(&mut self) {}
}

/// Repersents a bulk memory device, with its memory model
pub struct RegularMemory<M: MemoryModel> {
  data: Vec<u8>,
  model: M,
}

impl RegularMemory<TrivialModel> {
  pub fn with_content(data: Vec<u8>) -> Self {
    RegularMemory { data, model: TrivialModel::default() }
  }
}

impl RegularMemory<DRAMModel> {
  pub fn with_content_and_model(data: Vec<u8>, ds_cfg: &Path, ds_path: &Path) -> Self {
    RegularMemory { data, model: DRAMModel::new(ds_cfg, ds_path) }
  }
}

impl<M: MemoryModel + Send + 'static> RegularMemory<M> {
  fn execute_read(&mut self, addr: AddrInfo) -> &[u8] {
    &self.data[addr.as_range()]
  }

  fn execute_write(&mut self, addr: AddrInfo, data: &[u8], mask: Option<&[bool]>) {
    let mem_data = &mut self.data[addr.as_range()];
    memcpy_mask(mem_data, data, mask);
  }
}

impl<M: MemoryModel + Send + 'static> Device for RegularMemory<M> {
  fn req(&mut self, req: MemReq<'_>) -> bool {
    // dbg!(&req);
    let ident = MemIdent {
      id: req.id,
      req: req.addr,
      is_write: req.payload.is_write(),
    };
    self.model.push(ident);

    if let MemReqPayload::Write(data, mask) = req.payload {
      self.execute_write(req.addr, data, mask);
    }
    true
  }

  fn resp(&mut self) -> Option<MemResp<'_>> {
    let popped = self.model.pop()?;
    // dbg!(&popped);

    // Construct MemResp
    let payload = if popped.is_write {
      MemRespPayload::WriteAck
    } else {
      MemRespPayload::ReadBuffered(self.execute_read(popped.req))
    };
    // dbg!(&payload);

    Some(MemResp { id: popped.id, payload })
  }

  fn tick(&mut self) {
    self.model.tick();
  }
}

fn memcpy_mask(dst: &mut [u8], src: &[u8], mask: Option<&[bool]>) {
  for i in 0..src.len() {
    if mask.map_or(true, |m| m[i]) {
      dst[i] = src[i];
    }
  }
}

pub struct DeviceEntry {
  base_and_size: (u32, u32),
  device: Box<dyn Device>,
}

pub struct AddressSpace {
  devices: Vec<DeviceEntry>,
}

impl AddressSpace {
  pub fn req(&mut self, id: u64, addr: u32, len: u32, payload: MemReqPayload) -> bool {
    if let MemReqPayload::Write(data, mask) = payload {
      assert_eq!(len as usize, data.len());
      if let Some(ref m) = mask {
        assert_eq!(len as usize, m.len());
      }
    }
    let Some(device_idx) = self.find_device_idx(addr, len) else {
      panic!("write error (no device found), addr=0x{addr:08x}, len={len}B");
    };
    let dev_entry = &mut self.devices[device_idx];
    let addr = AddrInfo { offset: addr - dev_entry.base_and_size.0, len };
    dev_entry.device.req(MemReq { id, payload, addr })
  }

  pub fn resp(&mut self) -> Option<(u64, MemRespPayload)> {
    for dev in self.devices.iter_mut() {
      if let Some(r) = dev.device.resp() {
        return Some((r.id, r.payload));
      }
    }
    None
  }

  pub fn tick(&mut self) {
    for dev in self.devices.iter_mut() {
      dev.device.tick();
    }
  }

  fn find_device_idx(&self, addr: u32, len: u32) -> Option<usize> {
    for (idx, dev) in self.devices.iter().enumerate() {
      let (base, size) = dev.base_and_size;
      if base <= addr && addr - base < size {
        return if addr - base + len <= size {
          Some(idx)
        } else {
          None
        };
      }
    }

    None
  }
}

pub const RAM_BASE: u32 = 0x2000_0000;
pub const RAM_SIZE: u32 = 0xa000_0000;

/// Memory map:
/// - 0x0400_0000 - 0x0600_0000 : framebuffer
/// - 0x1000_0000 - 0x1000_1000 : simctrl
/// - 0x2000_0000 - 0xc000_0000 : sram
pub fn create_emu_addrspace_with_mem<M: MemoryModel + Send + 'static>(
  mem: RegularMemory<M>,
) -> (AddressSpace, ExitFlagRef) {
  const SIMCTRL_BASE: u32 = 0x1000_0000;
  const SIMCTRL_SIZE: u32 = 0x0000_1000; // one page
  const DISPLAY_BASE: u32 = 0x0400_0000;
  const DISPLAY_SIZE: u32 = 0x0200_0000;

  let exit_flag = ExitFlagRef::new();

  let devices = vec![
    mem.with_addr(RAM_BASE, RAM_SIZE),
    FrameBuffer::new().with_addr(DISPLAY_BASE, DISPLAY_SIZE),
    WrappedRegDevice::new(SimCtrl::new(exit_flag.clone())).with_addr(SIMCTRL_BASE, SIMCTRL_SIZE),
  ];
  (AddressSpace { devices }, exit_flag)
}
