#![allow(non_upper_case_globals)]
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]
// This disable warning of `u128` type usage.
// The `u128` type is not ABI stable, and is considered not FFI safe.
#![allow(improper_ctypes)]

include!(concat!(env!("OUT_DIR"), "/sail_h.rs"));

#[repr(C)]
pub(crate) struct zMisa {
    zMisa_chunk_0: mach_bits,
}

#[repr(C)]
pub(crate) struct zMcause {
    zMcause_chunk_0: mach_bits,
}

// ----------------------------------------------------------------------------
// Defined in sail model
// ----------------------------------------------------------------------------
extern "C" {
    fn model_init();
    fn model_fini();

    fn z_set_Misa_C(_: *mut zMisa, _: mach_bits) -> unit;
    fn z_set_Misa_D(_: *mut zMisa, _: mach_bits) -> unit;
    fn z_set_Misa_F(_: *mut zMisa, _: mach_bits) -> unit;
}

// ----------------------------------------------------------------------------
// Defined in sail-riscv model
// ----------------------------------------------------------------------------
extern "C" {
    fn zinit_model(_: unit) -> unit;
    fn zstep(_: sail_int) -> bool;
    fn ztick_clock(_: unit) -> unit;
    fn ztick_platform(_: unit) -> unit;

    pub static mut zxlen_val: mach_bits;
    pub static mut zhtif_done: bool;
    pub static mut zhtif_exit_code: mach_bits;
    pub static mut have_exception: bool;

    /* machine state */
    pub static mut zcur_privilege: u32;
    pub static mut zPC: mach_bits;
    pub static mut zx1: mach_bits;
    pub static mut zx2: mach_bits;
    pub static mut zx3: mach_bits;
    pub static mut zx4: mach_bits;
    pub static mut zx5: mach_bits;
    pub static mut zx6: mach_bits;
    pub static mut zx7: mach_bits;
    pub static mut zx8: mach_bits;
    pub static mut zx9: mach_bits;
    pub static mut zx10: mach_bits;
    pub static mut zx11: mach_bits;
    pub static mut zx12: mach_bits;
    pub static mut zx13: mach_bits;
    pub static mut zx14: mach_bits;
    pub static mut zx15: mach_bits;
    pub static mut zx16: mach_bits;
    pub static mut zx17: mach_bits;
    pub static mut zx18: mach_bits;
    pub static mut zx19: mach_bits;
    pub static mut zx20: mach_bits;
    pub static mut zx21: mach_bits;
    pub static mut zx22: mach_bits;
    pub static mut zx23: mach_bits;
    pub static mut zx24: mach_bits;
    pub static mut zx25: mach_bits;
    pub static mut zx26: mach_bits;
    pub static mut zx27: mach_bits;
    pub static mut zx28: mach_bits;
    pub static mut zx29: mach_bits;
    pub static mut zx30: mach_bits;
    pub static mut zx31: mach_bits;

    pub static mut zmstatus: mach_bits;
    pub static mut zmepc: mach_bits;
    pub static mut zmtval: mach_bits;
    pub static mut zsepc: mach_bits;
    pub static mut zstval: mach_bits;

    pub static mut zfloat_result: mach_bits;
    pub static mut zfloat_fflags: mach_bits;

    pub static mut zmcause: zMcause;
    pub static mut zscause: zMcause;

    pub static mut zminstret: mach_bits;

    pub static mut zmisa: zMisa;
}
