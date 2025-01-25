use std::{
  ffi::{c_char, c_int, c_void, CString},
  ptr, slice,
};

#[repr(C)]
pub struct MemoryVTable {
  addr_to_mem: unsafe extern "C" fn(memory: *mut c_void, addr: u64) -> *mut u8,
  mmio_load:
    unsafe extern "C" fn(memory: *mut c_void, addr: u64, len: usize, bytes: *mut u8) -> c_int,
  mmio_store:
    unsafe extern "C" fn(memory: *mut c_void, addr: u64, len: usize, bytes: *const u8) -> c_int,
}

extern "C" {
  fn t1emu_create(
    memory: *mut c_void,
    vtable: *const MemoryVTable,
    isa_set: *const c_char,
    vlen: usize,
  ) -> *mut c_void;
  fn t1emu_destroy(emu: *mut c_void);
  fn t1emu_reset_with_pc(emu: *mut c_void, new_pc: u64);
  fn t1emu_step_one(emu: *mut c_void);
}

pub trait SpikeMemory: 'static {
  fn addr_to_mem(&mut self, addr: u64) -> Option<&mut u8>;
  fn mmio_load(&mut self, addr: u64, data: &mut [u8]) -> bool;
  fn mmio_store(&mut self, addr: u64, data: &[u8]) -> bool;

  unsafe extern "C" fn _addr_to_mem(memory: *mut c_void, addr: u64) -> *mut u8
  where
    Self: Sized,
  {
    let memory = unsafe { &mut *(memory as *mut Self) };
    match memory.addr_to_mem(addr) {
      Some(v) => v,
      None => ptr::null_mut(),
    }
  }

  unsafe extern "C" fn _mmio_load(
    memory: *mut c_void,
    addr: u64,
    len: usize,
    bytes: *mut u8,
  ) -> c_int
  where
    Self: Sized,
  {
    let memory = unsafe { &mut *(memory as *mut Self) };
    let data = unsafe { slice::from_raw_parts_mut(bytes, len) };
    memory.mmio_load(addr, data) as c_int
  }
  unsafe extern "C" fn _mmio_store(
    memory: *mut c_void,
    addr: u64,
    len: usize,
    bytes: *const u8,
  ) -> c_int
  where
    Self: Sized,
  {
    let memory = unsafe { &mut *(memory as *mut Self) };
    let data = unsafe { slice::from_raw_parts(bytes, len) };
    memory.mmio_store(addr, data) as c_int
  }

  fn _make_vtable(&mut self) -> (*mut c_void, &'static MemoryVTable)
  where
    Self: Sized,
  {
    let vtable = &const {
      MemoryVTable {
        addr_to_mem: Self::_addr_to_mem,
        mmio_load: Self::_mmio_load,
        mmio_store: Self::_mmio_store,
      }
    };
    (self as *mut Self as *mut c_void, vtable)
  }
}

pub struct Spike {
  spike: *mut c_void,

  // TODO: use raw pointer if supported
  // `memory` is semantically mut borrowed by `spike`,
  // thus using a Box actually breaks Rust's alias rules.
  // However, raw fat pointers are unstable.
  memory: Box<dyn SpikeMemory>,
}

impl Drop for Spike {
  fn drop(&mut self) {
    unsafe {
      t1emu_destroy(self.spike);
    }
  }
}

impl Spike {
  pub fn new<M: SpikeMemory>(isa_set: &str, vlen: usize, memory: M) -> Self {
    assert!(vlen > 0 && vlen % 32 == 0);
    let isa_set = CString::new(isa_set).unwrap();
    let mut memory = Box::new(memory);
    let (_memory, _vtable) = M::_make_vtable(&mut *memory);
    let spike = unsafe { t1emu_create(_memory, _vtable, isa_set.as_ptr(), vlen) };

    Spike { spike, memory }
  }

  pub fn reset_with_pc(&mut self, new_pc: u64) {
    unsafe {
      t1emu_reset_with_pc(self.spike, new_pc);
    }
  }

  pub fn step_one(&mut self) {
    unsafe {
      t1emu_step_one(self.spike);
    }
  }
}
