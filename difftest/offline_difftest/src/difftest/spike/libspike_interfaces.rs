use libc::c_char;
use std::ffi::{CStr, CString};

pub struct Spike {
  spike: *mut (),
}

impl Spike {
  pub fn new(arch: &str, set: &str, lvl: &str) -> Self {
    let arch = CString::new(arch).unwrap();
    let set = CString::new(set).unwrap();
    let lvl = CString::new(lvl).unwrap();
    let spike = unsafe { spike_new(arch.as_ptr(), set.as_ptr(), lvl.as_ptr()) };
    Spike { spike }
  }

  pub fn get_proc(&self) -> Processor {
    let processor = unsafe { spike_get_proc(self.spike as *mut ()) };
    Processor { processor }
  }
}

impl Drop for Spike {
  fn drop(&mut self) {
    unsafe { spike_destruct(self.spike) }
  }
}

pub struct Processor {
  processor: *mut (),
}

impl Processor {
  pub fn disassemble(&self) -> String {
    let bytes = unsafe { proc_disassemble(self.processor) };
    let c_str = unsafe { CStr::from_ptr(bytes as *mut c_char) };
    format!("{}", c_str.to_string_lossy())
  }

  pub fn reset(&self) {
    unsafe { proc_reset(self.processor) }
  }

  pub fn get_state(&self) -> State {
    let state = unsafe { proc_get_state(self.processor) };
    State { state }
  }

  pub fn func(&self) -> u64 {
    unsafe { proc_func(self.processor) }
  }

  pub fn get_insn(&self) -> u64 {
    unsafe { proc_get_insn(self.processor) }
  }

  pub fn get_vreg_data(&self, idx: u32, offset: u32) -> u8 {
    unsafe { proc_get_vreg_data(self.processor, idx, offset) }
  }

  pub fn get_rs1(&self) -> u32 {
    unsafe { proc_get_rs1(self.processor) }
  }

  pub fn get_rs2(&self) -> u32 {
    unsafe { proc_get_rs1(self.processor) }
  }

  pub fn get_rd(&self) -> u32 {
    unsafe { proc_get_rd(self.processor) }
  }

  // vu
  pub fn vu_get_vtype(&self) -> u64 {
    unsafe { proc_vu_get_vtype(self.processor) }
  }

  pub fn vu_get_vxrm(&self) -> u32 {
    unsafe { proc_vu_get_vxrm(self.processor) }
  }

  pub fn vu_get_vnf(&self) -> u32 {
    unsafe { proc_vu_get_vnf(self.processor) }
  }

  pub fn vu_get_vill(&self) -> bool {
    unsafe { proc_vu_get_vill(self.processor) }
  }

  pub fn vu_get_vxsat(&self) -> bool {
    unsafe { proc_vu_get_vxsat(self.processor) }
  }

  pub fn vu_get_vl(&self) -> u32 {
    unsafe { proc_vu_get_vl(self.processor) }
  }

  pub fn vu_get_vstart(&self) -> u16 {
    unsafe { proc_vu_get_vstart(self.processor) }
  }
}

impl Drop for Processor {
  fn drop(&mut self) {
    unsafe { proc_destruct(self.processor) }
  }
}

pub struct State {
  state: *mut (),
}

impl State {
  pub fn set_pc(&self, pc: u64) {
    unsafe { state_set_pc(self.state, pc) }
  }

  pub fn get_pc(&self) -> u64 {
    unsafe { state_get_pc(self.state) }
  }

  pub fn handle_pc(&self, pc: u64) -> anyhow::Result<()> {
    match unsafe { state_handle_pc(self.state, pc) } {
      0 => Ok(()),
      _ => Err(anyhow::anyhow!("Error handling pc")),
    }
  }

  pub fn get_reg_write_size(&self) -> u32 {
    unsafe { state_get_reg_write_size(self.state) }
  }

  pub fn get_reg(&self, idx: u32, is_fp: bool) -> u32 {
    unsafe { state_get_reg(self.state, idx, is_fp) }
  }

  pub fn get_mem_write_size(&self) -> u32 {
    unsafe { state_get_mem_write_size(self.state) }
  }

  pub fn get_mem_write(&self, index: u32) -> (u32, u64, u8) {
    let addr = unsafe { state_get_mem_write_addr(self.state, index) };
    let value = unsafe { state_get_mem_write_value(self.state, index) };
    let size_by_byte = unsafe { state_get_mem_write_size_by_byte(self.state, index) };
    (addr, value, size_by_byte)
  }

  pub fn get_mem_read_size(&self) -> u32 {
    unsafe { state_get_mem_read_size(self.state) }
  }

  pub fn get_mem_read(&self, index: u32) -> (u32, u8) {
    let addr = unsafe { state_get_mem_read_addr(self.state, index) };
    let size_by_byte = unsafe { state_get_mem_read_size_by_byte(self.state, index) };
    (addr, size_by_byte)
  }

  pub fn clear(&self) {
    unsafe { state_clear(self.state) }
  }

  pub fn exit(&self) -> u64 {
    unsafe { state_exit(self.state) }
  }
}

impl Drop for State {
  fn drop(&mut self) {
    unsafe { state_destruct(self.state) }
  }
}

type FfiCallback = extern "C" fn(u64) -> *mut u8;

#[link(name = "spike_interfaces")]
extern "C" {
  pub fn spike_register_callback(callback: FfiCallback);
  fn spike_new(arch: *const c_char, set: *const c_char, lvl: *const c_char) -> *mut ();
  fn spike_get_proc(spike: *mut ()) -> *mut ();
  fn spike_destruct(spike: *mut ());
  fn proc_disassemble(proc: *mut ()) -> *mut c_char;
  fn proc_reset(proc: *mut ());
  fn proc_get_state(proc: *mut ()) -> *mut ();
  fn proc_func(proc: *mut ()) -> u64;
  fn proc_get_insn(proc: *mut ()) -> u64;
  fn proc_get_vreg_data(proc: *mut (), vreg_idx: u32, vreg_offset: u32) -> u8;
  fn proc_get_rs1(proc: *mut ()) -> u32;
  fn proc_get_rs2(proc: *mut ()) -> u32;
  fn proc_get_rd(proc: *mut ()) -> u32;

  fn proc_vu_get_vtype(proc: *mut ()) -> u64;
  fn proc_vu_get_vxrm(proc: *mut ()) -> u32;
  fn proc_vu_get_vnf(proc: *mut ()) -> u32;
  fn proc_vu_get_vill(proc: *mut ()) -> bool;
  fn proc_vu_get_vxsat(proc: *mut ()) -> bool;
  fn proc_vu_get_vl(proc: *mut ()) -> u32;
  fn proc_vu_get_vstart(proc: *mut ()) -> u16;

  fn proc_destruct(proc: *mut ());
  fn state_set_pc(state: *mut (), pc: u64);
  fn state_get_pc(state: *mut ()) -> u64;
  fn state_get_reg(state: *mut (), index: u32, is_fp: bool) -> u32;
  fn state_get_reg_write_size(state: *mut ()) -> u32;
  fn state_get_mem_write_size(state: *mut ()) -> u32;
  fn state_get_mem_write_addr(state: *mut (), index: u32) -> u32;
  fn state_get_mem_write_value(state: *mut (), index: u32) -> u64;
  fn state_get_mem_write_size_by_byte(state: *mut (), index: u32) -> u8;
  fn state_get_mem_read_size(state: *mut ()) -> u32;
  fn state_get_mem_read_addr(state: *mut (), index: u32) -> u32;
  fn state_get_mem_read_size_by_byte(state: *mut (), index: u32) -> u8;
  fn state_handle_pc(state: *mut (), pc: u64) -> u64;
  fn state_clear(state: *mut ());
  fn state_destruct(state: *mut ());
  fn state_exit(state: *mut ()) -> u64;
}
