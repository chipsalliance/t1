use std::{
  any::Any, cell::{Cell, RefCell}, collections::{BinaryHeap, VecDeque}, ffi::CString, os::unix::ffi::OsStrExt, path::Path, rc::Rc
};

use framebuffer::FrameBuffer;
use simctrl::{ExitFlagRef, SimCtrl};

use crate::get_t;

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

pub enum MemReqPayload<'a> {
  Read,
  Write(&'a [u8], Option<&'a [bool]>),
}

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

pub enum MemRespPayload<'a> {
  ReadBuffered(&'a [u8]),
  ReadRegister([u8; 4]),
  WriteAck,
}

pub struct MemResp<'a> {
  pub id: u64,
  pub payload: MemRespPayload<'a>,
}

// Caller is reponsible to ensure the following conditions hold:
//   addr.len > 0
//   addr.len == data.len()
//   addr.len == mask.len() (if mask present)
// However, since the functions are safe,
// even if contracts violate, implementions must not break memory safety,
pub trait Device: Any + Send + Sync {
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
struct WrappedRegDevice<RD: RegDevice> {
  device: RD,
  pending: Option<MemResp<'static>>,
}

impl<RD: RegDevice> WrappedRegDevice<RD> {
  fn new(device: RD) -> Self {
    Self {
      device,
      pending: None,
    }
  }
}

impl<T: RegDevice + Send + Sync + 'static> Device for WrappedRegDevice<T> {
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

struct MemIdent {
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

trait MemoryModel {
  fn push(&mut self, req: MemIdent);
  fn pop(&mut self) -> Option<MemIdent>;
  fn tick(&mut self);
}

struct InflightMem {
  id: u64,
  req: AddrInfo,
  is_write: bool,

  /// Addr of first byte of next request (addr)
  send_ptr: u32,
  /// Addr of first byte of next response
  done_ptr: u32,
}

impl InflightMem {
  fn from_ident(ident: MemIdent, alignment: u32) -> Self {
    let aligned = (ident.req.offset / alignment) * alignment;
    Self {
      id: ident.id,
      req: ident.req,
      is_write: ident.is_write,

      send_ptr: aligned,
      done_ptr: aligned,
    }
  }

  fn sent(&self) -> bool {
    self.send_ptr >= self.req.offset + self.req.len
  }
  fn done(&self) -> bool {
    self.done_ptr >= self.req.offset + self.req.len
  }
}

pub struct DRAMModel {
  sys: dramsim3::MemorySystem,
  inflights: Rc<RefCell<Vec<InflightMem>>>,
  cached: BinaryHeap<u32>,
}

impl DRAMModel {
  fn new(ds_cfg: impl AsRef<Path>, ds_path: impl AsRef<Path>) -> Self {
    let inflights: Rc<RefCell<Vec<InflightMem>>> = Rc::new(RefCell::new(Vec::new()));
    let inflights_clone = inflights.clone();
    let chunk_size: Rc<Cell<u32>> = Rc::new(Cell::new(0));
    let chunk_size_clone = chunk_size.clone();
    let ds_cfg_cstr = CString::new(ds_cfg.as_ref().as_os_str().as_bytes()).expect("Incorrect path format");
    let ds_path_cstr = CString::new(ds_path.as_ref().as_os_str().as_bytes()).expect("Incorrect path format");
    let sys =
      dramsim3::MemorySystem::new(&ds_cfg_cstr, &ds_path_cstr, move |addr, is_write| {
        for req in inflights_clone.borrow_mut().iter_mut() {
          if req.done_ptr as u64 == addr && req.is_write == is_write {
            // TODO: pass chunk_size from dramsim3
            req.done_ptr += chunk_size.get();
            return;
          }
        }
        // TODO: log and immediately exit
        println!("Unexpected memory response!");
      });
    let ret = DRAMModel {
      sys,
      inflights: inflights,
      cached: BinaryHeap::new(),
    };

    chunk_size_clone.set(ret.req_size());
    ret
  }

  // Size of each request to DRAM, in bytes
  fn req_size(&self) -> u32 {
    (self.sys.burst_length() * self.sys.bus_bits() / 8) as u32
  }
}

impl MemoryModel for DRAMModel {
  fn push(&mut self, req: MemIdent) {
    // TODO: done if in cache
    self.inflights.borrow_mut().push(InflightMem::from_ident(req, self.req_size()));
    // TODO: look for conflighting ID
  }

  fn pop(&mut self) -> Option<MemIdent> {
    // Take exact one of the inflight request that are fully done
    let mut inf = self.inflights.borrow_mut();
    for i in 0..inf.len() {
      if inf[i].done() {
        return Some(inf.remove(i).into())
      }
    }
    None
  }

  fn tick(&mut self) {
    // TODO: tick by faster clock

    let mut inflights = self.inflights.borrow_mut();
    for inflight in inflights.iter_mut() {
      if inflight.sent() {
        continue;
      }
      let next_addr = inflight.send_ptr;
      if !self.sys.can_add(next_addr as u64, inflight.is_write) {
        continue;
      }
      self.sys.add(next_addr as u64, inflight.is_write);
      inflight.send_ptr += self.req_size();
    }
  }
}

#[derive(Default)]
struct TrivialModel {
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

pub struct RegularMemory<M: MemoryModel> {
  data: Vec<u8>,
  model: M,
}

impl RegularMemory<TrivialModel> {
  pub fn with_content(data: Vec<u8>) -> Self {
    RegularMemory {
      data,
      model: TrivialModel::default(),
    }
  }

  pub fn with_size(size: u32) -> Self {
    Self::with_content(vec![0; size as usize])
  }
}

impl RegularMemory<DRAMModel> {
  pub fn with_size_and_model(
    size: u32,
    ds_cfg: impl AsRef<Path>,
    ds_path: impl AsRef<Path>,
  ) -> Self {
    RegularMemory {
      data: vec![0; size as usize],
      model: DRAMModel::new(ds_cfg, ds_path),
    }
  }
}

impl<M: MemoryModel + Send + Sync + 'static> RegularMemory<M> {
  fn execute_read(&mut self, addr: AddrInfo) -> &[u8] {
    &self.data[addr.as_range()]
  }

  fn execute_write(&mut self, addr: AddrInfo, data: &[u8], mask: Option<&[bool]>) {
    let mem_data = &mut self.data[addr.as_range()];
    memcpy_mask(mem_data, data, mask);
  }
}

impl<M: MemoryModel + Send + Sync + 'static> Device for RegularMemory<M> {
  fn req(&mut self, req: MemReq<'_>) -> bool {
    let ident = MemIdent {
      id: req.id,
      req: req.addr,
      is_write: req.payload.is_write()
    };
    self.model.push(ident);

    if let MemReqPayload::Write(data, mask) = req.payload {
      self.execute_write(req.addr, data, mask);
    }
    true
  }

  fn resp(&mut self) -> Option<MemResp<'_>> {
    let popped = self.model.pop()?;

    // Construct MemResp
    let payload = if popped.is_write {
      MemRespPayload::WriteAck
    } else {
      MemRespPayload::ReadBuffered(self.execute_read(popped.req))
    };

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
  next_tick: u64,
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
        return Some((r.id, r.payload))
      }
    }
    None
  }

  pub fn tick(&mut self) {
    let desired_tick = get_t();
    if self.next_tick != 0 && desired_tick != self.next_tick {
      panic!("Skipped a tick!");
    }

    self.next_tick = desired_tick + 1;
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

pub const SRAM_BASE: u32 = 0x2000_0000;
pub const SRAM_SIZE: u32 = 0xa000_0000;

/// Memory map:
/// - 0x0400_0000 - 0x0600_0000 : framebuffer
/// - 0x1000_0000 - 0x1000_1000 : simctrl
/// - 0x2000_0000 - 0xc000_0000 : sram
pub fn create_emu_addrspace_with_initmem(initmem: Vec<u8>) -> (AddressSpace, ExitFlagRef) {
  const SIMCTRL_BASE: u32 = 0x1000_0000;
  const SIMCTRL_SIZE: u32 = 0x0000_1000; // one page
  const DISPLAY_BASE: u32 = 0x0400_0000;
  const DISPLAY_SIZE: u32 = 0x0200_0000;

  let exit_flag = ExitFlagRef::new();

  let devices = vec![
    RegularMemory::with_content(initmem).with_addr(SRAM_BASE, SRAM_SIZE),
    FrameBuffer::new().with_addr(DISPLAY_BASE, DISPLAY_SIZE),
    WrappedRegDevice::new(SimCtrl::new(exit_flag.clone())).with_addr(SIMCTRL_BASE, SIMCTRL_SIZE),
  ];
  (AddressSpace { devices, next_tick: 0 }, exit_flag)
}
