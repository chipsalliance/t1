use zerocopy::IntoBytes as _;

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
            model::__PC = entry;
        }
    }

    pub(crate) fn reset_states(&mut self) {
        unsafe { model::ASL_ResetConfigAndState_0() }
    }

    pub(crate) fn step(&mut self, state: &mut SimulatorState) {
        self.with_setup_callback(state, || unsafe { model::ASL_Step_0() })
    }

    /// `get_pc` is the current value of ASL model internal `PC` register.
    pub(crate) fn get_pc(&self) -> u32 {
        unsafe { model::__PC }
    }

    pub(crate) fn get_register(&self, i: u8) -> u32 {
        unsafe { model::X_read_0(i) }
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
unsafe extern "C" fn FFI_instruction_fetch_half_0(pc: u32) -> model::FFI_ReadResult_N_16 {
    let state = unsafe { get_state() };

    if let Some(insn) = state.inst_fetch(pc) {
        model::FFI_ReadResult_N_16 {
            success: true,
            data: insn,
        }
    } else {
        model::FFI_ReadResult_N_16 {
            success: false,
            data: 0,
        }
    }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_read_physical_memory_8bits_0(address: u32) -> model::FFI_ReadResult_N_8 {
    let state = unsafe { get_state() };
    if let Ok(mem) = state.req_bus_read(address) {
        model::FFI_ReadResult_N_8 {
            success: true,
            data: u8::from_le_bytes(mem),
        }
    } else {
        model::FFI_ReadResult_N_8 {
            success: false,
            data: 0,
        }
    }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_read_physical_memory_16bits_0(address: u32) -> model::FFI_ReadResult_N_16 {
    let state = unsafe { get_state() };
    if let Ok(mem) = state.req_bus_read(address) {
        model::FFI_ReadResult_N_16 {
            success: true,
            data: u16::from_le_bytes(mem),
        }
    } else {
        model::FFI_ReadResult_N_16 {
            success: false,
            data: 0,
        }
    }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_read_physical_memory_32bits_0(address: u32) -> model::FFI_ReadResult_N_32 {
    let state = unsafe { get_state() };
    if let Ok(mem) = state.req_bus_read(address) {
        model::FFI_ReadResult_N_32 {
            success: true,
            data: u32::from_le_bytes(mem),
        }
    } else {
        model::FFI_ReadResult_N_32 {
            success: false,
            data: 0,
        }
    }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_physical_memory_8bits_0(address: u32, data: u8) -> bool {
    let state = unsafe { get_state() };
    state.req_bus_write(address, &data.to_le_bytes()).is_ok()
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_physical_memory_16bits_0(address: u32, data: u16) -> bool {
    let state = unsafe { get_state() };
    state.req_bus_write(address, &data.to_le_bytes()).is_ok()
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_physical_memory_32bits_0(address: u32, data: u32) -> bool {
    let state = unsafe { get_state() };
    state.req_bus_write(address, &data.to_le_bytes()).is_ok()
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
unsafe extern "C" fn FFI_write_FPR_hook_0(reg_idx: u8, data: u32) {
    let state = unsafe { get_state() };
    state.write_fp_register(reg_idx, data);
}

#[allow(improper_ctypes_definitions)]
#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_CSR_hook_0(name: *const c_char, data: u32) {
    let state = unsafe { get_state() };
    let name = unsafe { CStr::from_ptr(name) };
    let name = String::from_utf8_lossy(name.to_bytes());
    state.write_csr(&name, data);
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_write_VREG_vlen256_hook_0(vd: u8, data0: u64, data1: u64, data2: u64, data3: u64) {
    let state = unsafe { get_state() };
    let data: [u64; 4] = [data0, data1, data2, data3];

    state.write_vector_register(vd, data.as_bytes());
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_debug_log_issue_0(pc: u32, insn: u32, is_c: bool) {
    let state = unsafe { get_state() };
    state.debug_log_issue(pc, insn, is_c);
}

#[unsafe(no_mangle)]
unsafe extern "C" fn ffi_commit_insn_0(pc: u32, insn: u32, is_c: bool) {
    let state = unsafe { get_state() };
    state.commit_log_insn(pc, insn, is_c);
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
unsafe extern "C" fn ffi_debug_trap_xcpt_0(cause: i128, tval: u32) {
    let state = unsafe { get_state() };
    state.debug_trap_xcpt(cause, tval);
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

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_machine_external_interrupt_pending_0() -> u32 {
    0
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_machine_time_interrupt_pending_0() -> u32 {
    0
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_load_reserved_0(addr: u32) -> model::FFI_ReadResult_N_32 {
    let state = unsafe { get_state() };
    if let Some(data) = state.load_reserved(addr) {
        model::FFI_ReadResult_N_32 {
            success: true,
            data: u32::from_le_bytes(data),
        }
    } else {
        model::FFI_ReadResult_N_32 {
            success: false,
            data: 0,
        }
    }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_store_conditional_0(addr: u32, data: u32) -> bool {
    let state = unsafe { get_state() };

    state.store_conditional(addr, &data.to_le_bytes())
}

#[unsafe(no_mangle)]
unsafe extern "C" fn FFI_amo_0(
    amo_op_type: model::AmoOperationType,
    addr: u32,
    src2: u32,
    is_aq: bool,
    is_rl: bool,
) -> model::FFI_ReadResult_N_32 {
    let state = unsafe { get_state() };

    let op: fn(u32, u32) -> u32 = match amo_op_type {
        model::AmoOperationType_AMO_SWAP => |_: u32, new: u32| -> u32 { new },
        model::AmoOperationType_AMO_ADD => |old: u32, new: u32| -> u32 { old + new },
        model::AmoOperationType_AMO_AND => |old: u32, new: u32| -> u32 { old & new },
        model::AmoOperationType_AMO_OR => |old: u32, new: u32| -> u32 { old | new },
        model::AmoOperationType_AMO_XOR => |old: u32, new: u32| -> u32 { old ^ new },
        model::AmoOperationType_AMO_MAX => {
            |old: u32, new: u32| -> u32 { i32::max(old as i32, new as i32) as u32 }
        }
        model::AmoOperationType_AMO_MIN => {
            |old: u32, new: u32| -> u32 { i32::min(old as i32, new as i32) as u32 }
        }
        model::AmoOperationType_AMO_MAXU => u32::max,
        model::AmoOperationType_AMO_MINU => u32::min,
        _ => unreachable!("Invalid AMO operation {amo_op_type} met"),
    };

    if let Some(old) = state.amo_exec(op, addr, src2, is_aq, is_rl) {
        model::FFI_ReadResult_N_32 {
            success: true,
            data: old,
        }
    } else {
        model::FFI_ReadResult_N_32 {
            success: false,
            data: 0,
        }
    }
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
