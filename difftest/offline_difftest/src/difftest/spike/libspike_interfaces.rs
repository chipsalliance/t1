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
	pub fn disassemble(&self, pc: u64) -> std::borrow::Cow<str> {
		let bytes = unsafe { proc_disassemble(self.processor, pc) };
		let c_str = unsafe { CStr::from_ptr(bytes as *mut c_char) };
		c_str.to_string_lossy()
	}

	pub fn reset(&self) {
		unsafe { proc_reset(self.processor) }
	}

	pub fn get_state(&self) -> State {
		let state = unsafe { proc_get_state(self.processor) };
		State { state }
	}

	pub fn func(&self, pc: u64) -> u64 {
		unsafe { proc_func(self.processor, pc) }
	}

	pub fn get_insn(&self, pc: u64) -> u64 {
		unsafe { proc_get_insn(self.processor, pc) }
	}

	pub fn get_vreg_addr(&self) -> *mut u8 {
		unsafe { proc_get_vreg_addr(self.processor) }
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
	fn proc_disassemble(proc: *mut (), pc: u64) -> *mut c_char;
	fn proc_reset(proc: *mut ());
	fn proc_get_state(proc: *mut ()) -> *mut ();
	fn proc_func(proc: *mut (), pc: u64) -> u64;
	fn proc_get_insn(proc: *mut (), pc: u64) -> u64;
	fn proc_get_vreg_addr(proc: *mut ()) -> *mut u8;
	fn proc_destruct(proc: *mut ());
	fn state_set_pc(state: *mut (), pc: u64);
	fn state_get_pc(state: *mut ()) -> u64;
	fn state_handle_pc(state: *mut (), pc: u64) -> u64;
	fn state_destruct(state: *mut ());
	fn state_exit(state: *mut ()) -> u64;
}
