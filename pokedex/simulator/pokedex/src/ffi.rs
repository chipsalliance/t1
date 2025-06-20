use crate::model;
use crate::simulator::SIM_HANDLE;
use std::ffi::CStr;
use std::os::raw::c_char;

/// `reset_vector` set PC to given `entry` address. This function remain unsafe to make sure end
/// user knows the side effect of this function.
pub(crate) unsafe fn reset_vector(entry: u32) {
    unsafe {
        model::PC = entry;
    }
}

pub(crate) unsafe fn reset_states() {
    unsafe { model::ASL_Reset_0() }
}

pub(crate) unsafe fn step() {
    unsafe { model::ASL_Step_0() }
}

/// `get_pc` is the current value of ASL model internal `PC` register.
pub(crate) fn get_pc() -> u32 {
    unsafe { model::PC }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_instruction_fetch_0(pc: u32) -> u32 {
    SIM_HANDLE.with(|core| core.inst_fetch(pc))
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_read_physical_memory_8bits_0(address: u32) -> u8 {
    SIM_HANDLE.with(|core| u8::from_le_bytes(core.phy_readmem(address)))
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_read_physical_memory_16bits_0(address: u32) -> u16 {
    SIM_HANDLE.with(|core| u16::from_le_bytes(core.phy_readmem(address)))
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_read_physical_memory_32bits_0(address: u32) -> u32 {
    SIM_HANDLE.with(|core| u32::from_le_bytes(core.phy_readmem(address)))
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_physical_memory_8bits_0(address: u32, data: u8) {
    SIM_HANDLE.with(|core| {
        core.phy_write_mem(address, data);
    })
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_physical_memory_16bits_0(address: u32, data: u16) {
    SIM_HANDLE.with(|core| {
        core.phy_write_mem(address, data);
    })
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_physical_memory_32bits_0(address: u32, data: u32) {
    SIM_HANDLE.with(|core| {
        core.phy_write_mem(address, data);
    })
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_emulator_do_fence_0() {
    SIM_HANDLE.with(|core| {
        core.fence_i();
    })
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_GPR_hook_0(reg_idx: u8, data: u32) {
    SIM_HANDLE.with(|core| {
        core.write_register(reg_idx, data);
    })
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_print_str_0(s: *const c_char) {
    let cstr = unsafe { CStr::from_ptr(s) };
    let rstr = String::from_utf8_lossy(cstr.to_bytes());
    SIM_HANDLE.with(|core| {
        core.print_string(&rstr);
    })
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_print_bits_hex_0(data: u32) {
    SIM_HANDLE.with(|core| {
        core.print_bits_hex(data);
    })
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_ebreak_0() {
    SIM_HANDLE.with(|core| {
        core.handle_ebreak();
    })
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_ecall_0() {
    SIM_HANDLE.with(|core| {
        core.handle_ecall();
    })
}
