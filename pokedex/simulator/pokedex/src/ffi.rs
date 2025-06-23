use crate::model;
use crate::simulator::{SimulatorHandle, SimulatorState};
use std::ffi::CStr;
use std::os::raw::c_char;
use std::ptr::null_mut;

impl SimulatorHandle {
    /// `reset_vector` set PC to given `entry` address. This function remain unsafe to make sure end
    /// user knows the side effect of this function.
    pub(crate) fn reset_vector(&mut self, entry: u32) {
        unsafe {
            model::PC = entry;
        }
    }

    pub(crate) fn reset_states(&mut self) {
        unsafe { model::ASL_Reset_0() }
    }

    pub(crate) fn step(&mut self, state: &mut SimulatorState) {
        self.with_setup_callback(state, || unsafe { model::ASL_Step_0() })
    }

    /// `get_pc` is the current value of ASL model internal `PC` register.
    pub(crate) fn get_pc(&self) -> u32 {
        unsafe { model::PC }
    }

    fn with_setup_callback<F: FnOnce()>(&mut self, state: &mut SimulatorState, f: F) {
        let callback = &raw mut CALLBACK_STATE;
        // Safety: SimulatorHandle has at most one instance,
        // which prevents data race and reentrance.
        unsafe {
            *callback = state;
            f();
            *callback = null_mut();
        }
    }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_instruction_fetch_0(pc: u32) -> u32 {
    let state = unsafe { get_state() };
    state.inst_fetch(pc)
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_read_physical_memory_8bits_0(address: u32) -> u8 {
    let state = unsafe { get_state() };
    u8::from_le_bytes(state.phy_readmem(address))
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_read_physical_memory_16bits_0(address: u32) -> u16 {
    let state = unsafe { get_state() };
    u16::from_le_bytes(state.phy_readmem(address))
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_read_physical_memory_32bits_0(address: u32) -> u32 {
    let state = unsafe { get_state() };
    u32::from_le_bytes(state.phy_readmem(address))
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_physical_memory_8bits_0(address: u32, data: u8) {
    let state = unsafe { get_state() };
    state.phy_write_mem(address, data);
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_physical_memory_16bits_0(address: u32, data: u16) {
    let state = unsafe { get_state() };
    state.phy_write_mem(address, data);
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_physical_memory_32bits_0(address: u32, data: u32) {
    let state = unsafe { get_state() };
    state.phy_write_mem(address, data);
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_emulator_do_fence_0() {
    let state = unsafe { get_state() };
    state.fence_i();
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_GPR_hook_0(reg_idx: u8, data: u32) {
    let state = unsafe { get_state() };
    state.write_register(reg_idx, data);
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_print_str_0(s: *const c_char) {
    let state = unsafe { get_state() };
    let cstr = unsafe { CStr::from_ptr(s) };
    let rstr = String::from_utf8_lossy(cstr.to_bytes());
    state.print_string(&rstr);
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_print_bits_hex_0(data: u32) {
    let state = unsafe { get_state() };
    state.print_bits_hex(data);
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_ebreak_0() {
    let state = unsafe { get_state() };
    state.handle_ebreak();
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_ecall_0() {
    let state = unsafe { get_state() };
    state.handle_ecall();
}

static mut CALLBACK_STATE: *mut SimulatorState = std::ptr::null_mut();

// It should only be called within ASL callback,
// typically at the very beginning only ONCE.
//
// If you call it twice, make sure not violate &mut alias rule.
//
// Panic: panic if [`Simulator::with_setup_callback`] is not used.`
//
// Safety: If you call it twice, make sure not violate &mut alias rule.
// You shall not call it outside ASL callback.
unsafe fn get_state<'a>() -> &'a mut SimulatorState {
    // Safety: CALLBACK_STATE could be only set from [`SimulatorHandle::with_setup_callback`],
    // thus if CALLBACK_STATE is non-null, it must be valid
    unsafe {
        // CALLBACK is static mut, read it to a local variable first
        let p = CALLBACK_STATE;

        assert!(!p.is_null(), "CALLBACK_STATE is not setup");
        &mut *p
    }
}
