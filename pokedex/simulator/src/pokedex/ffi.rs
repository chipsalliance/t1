use std::{
    ffi::{CStr, CString, c_char, c_int, c_void},
    marker::PhantomData,
    ptr::NonNull,
    slice::from_raw_parts,
};
use tracing::{error, info};

use super::{bus::AtomicOp, simulator::Inst};

#[allow(nonstandard_style)]
mod raw {
    include!(concat!(env!("OUT_DIR"), "/pokedex_vtable.rs"));
}

// See include/pokedex_vtable.h for more
pub trait PokedexCallbackMem {
    type CbMemError;

    fn inst_fetch_2(&mut self, addr: u32) -> Result<u16, Self::CbMemError>;
    fn read_mem_u8(&mut self, addr: u32) -> Result<u8, Self::CbMemError>;
    fn read_mem_u16(&mut self, addr: u32) -> Result<u16, Self::CbMemError>;
    fn read_mem_u32(&mut self, addr: u32) -> Result<u32, Self::CbMemError>;
    fn write_mem_u8(&mut self, addr: u32, value: u8) -> Result<(), Self::CbMemError>;
    fn write_mem_u16(&mut self, addr: u32, value: u16) -> Result<(), Self::CbMemError>;
    fn write_mem_u32(&mut self, addr: u32, value: u32) -> Result<(), Self::CbMemError>;
    fn amo_mem_u32(&mut self, addr: u32, op: AtomicOp, value: u32)
    -> Result<u32, Self::CbMemError>;
}

// See include/pokedex_vtable.h for more
pub trait PokedexCallbackTrace {
    fn log_inst_issue(&mut self, pc: u32, inst: Inst);
    fn log_inst_commit(&mut self);
    fn log_inst_xcpt(&mut self, xcause: u32, xtval: u32);

    fn log_write_xreg(&mut self, rd: u8, value: u32);
    fn log_write_freg(&mut self, fd: u8, value: u32);
    fn log_write_vreg(&mut self, vd: u8, value: &[u8]);
    fn log_write_csr(&mut self, name: &str, value: u32);

    // informational only
    fn debug_write(&mut self, message: &CStr);
}

pub trait PokedexCallback: PokedexCallbackMem + PokedexCallbackTrace + 'static {}
impl<T: PokedexCallbackMem + PokedexCallbackTrace + 'static> PokedexCallback for T {}

struct MakeVTable<T: PokedexCallbackMem + PokedexCallbackTrace + 'static>(PhantomData<T>);

trait ResultExt1 {
    fn as_int(self) -> c_int;
}
trait ResultExt2<T> {
    fn as_int_ret(self, ret: *mut T) -> c_int;
}

impl<E> ResultExt1 for Result<(), E> {
    fn as_int(self) -> c_int {
        match self {
            Ok(()) => 0,
            Err(_e) => 1,
        }
    }
}

impl<E, T: Copy> ResultExt2<T> for Result<T, E> {
    fn as_int_ret(self, ret: *mut T) -> c_int {
        match self {
            Ok(value) => {
                unsafe {
                    *ret = value;
                }
                0
            }
            Err(_e) => 1,
        }
    }
}

impl<T: PokedexCallbackMem + PokedexCallbackTrace + 'static> MakeVTable<T> {
    unsafe extern "C" fn inst_fetch_2(model: *mut c_void, addr: u32, ret: *mut u16) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.inst_fetch_2(addr).as_int_ret(ret)
    }
    unsafe extern "C" fn read_mem_1(model: *mut c_void, addr: u32, ret: *mut u8) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.read_mem_u8(addr).as_int_ret(ret)
    }
    unsafe extern "C" fn read_mem_2(model: *mut c_void, addr: u32, ret: *mut u16) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.read_mem_u16(addr).as_int_ret(ret)
    }
    unsafe extern "C" fn read_mem_4(model: *mut c_void, addr: u32, ret: *mut u32) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.read_mem_u32(addr).as_int_ret(ret)
    }
    unsafe extern "C" fn write_mem_1(model: *mut c_void, addr: u32, value: u8) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.write_mem_u8(addr, value).as_int()
    }
    unsafe extern "C" fn write_mem_2(model: *mut c_void, addr: u32, value: u16) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.write_mem_u16(addr, value).as_int()
    }
    unsafe extern "C" fn write_mem_4(model: *mut c_void, addr: u32, value: u32) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.write_mem_u32(addr, value).as_int()
    }
    unsafe extern "C" fn amo_mem_4(
        model: *mut c_void,
        addr: u32,
        amo_op: u8,
        value: u32,
        ret: *mut u32,
    ) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        let op = match amo_op as u32 {
            raw::POKEDEX_AMO_SWAP => AtomicOp::Swap,
            raw::POKEDEX_AMO_ADD => AtomicOp::Add,
            raw::POKEDEX_AMO_AND => AtomicOp::And,
            raw::POKEDEX_AMO_OR => AtomicOp::Or,
            raw::POKEDEX_AMO_XOR => AtomicOp::Xor,
            raw::POKEDEX_AMO_MIN => AtomicOp::Min,
            raw::POKEDEX_AMO_MAX => AtomicOp::Max,
            raw::POKEDEX_AMO_MINU => AtomicOp::Minu,
            raw::POKEDEX_AMO_MAXU => AtomicOp::Maxu,
            _ => unreachable!("unknown amo opcode ({amo_op})"),
        };
        model.amo_mem_u32(addr, op, value).as_int_ret(ret)
    }
    unsafe extern "C" fn lr_mem_4(model: *mut c_void, _addr: u32, _ret: *mut u32) -> c_int {
        let _model = unsafe { &mut *(model as *mut T) };
        todo!("LR instruction not implemented")
    }
    unsafe extern "C" fn sc_mem_4(
        model: *mut c_void,
        _addr: u32,
        _value: u32,
        _ret: *mut u32,
    ) -> c_int {
        let _model = unsafe { &mut *(model as *mut T) };
        todo!("SC instruction not implemented")
    }
    unsafe extern "C" fn log_inst_issue(model: *mut c_void, pc: u32, inst: u32) {
        let model = unsafe { &mut *(model as *mut T) };
        model.log_inst_issue(pc, Inst::NC(inst));
    }
    unsafe extern "C" fn log_inst_issue_c(model: *mut c_void, pc: u32, inst: u16) {
        let model = unsafe { &mut *(model as *mut T) };
        model.log_inst_issue(pc, Inst::C(inst));
    }
    unsafe extern "C" fn log_inst_commit(model: *mut c_void) {
        let model = unsafe { &mut *(model as *mut T) };
        model.log_inst_commit();
    }
    unsafe extern "C" fn log_inst_xcpt(model: *mut c_void, xcause: u32, xtval: u32) {
        let model = unsafe { &mut *(model as *mut T) };
        model.log_inst_xcpt(xcause, xtval);
    }
    unsafe extern "C" fn log_intr_taken(_model: *mut c_void, xepc: u32, intr_code: u32) {
        todo!("log_intr_taken, xepc={xepc:#010x}, intr_code={intr_code}");
    }
    unsafe extern "C" fn log_write_xreg(model: *mut c_void, rd: u8, value: u32) {
        let model = unsafe { &mut *(model as *mut T) };
        model.log_write_xreg(rd, value);
    }
    unsafe extern "C" fn log_write_freg(model: *mut c_void, fd: u8, value: u32) {
        let model = unsafe { &mut *(model as *mut T) };
        model.log_write_freg(fd, value);
    }
    unsafe extern "C" fn log_write_vreg(
        model: *mut c_void,
        vd: u8,
        value: *const u8,
        byte_len: usize,
    ) {
        let model = unsafe { &mut *(model as *mut T) };
        let value = unsafe { from_raw_parts(value, byte_len) };
        model.log_write_vreg(vd, value);
    }
    unsafe extern "C" fn log_write_csr(model: *mut c_void, name: *const c_char, value: u32) {
        let model = unsafe { &mut *(model as *mut T) };
        let name = unsafe { CStr::from_ptr(name) }.to_str().unwrap();
        model.log_write_csr(name, value);
    }
    unsafe extern "C" fn debug_print(model: *mut c_void, message: *const c_char) {
        let model = unsafe { &mut *(model as *mut T) };
        model.debug_write(unsafe { CStr::from_ptr(message) });
    }
    const VTABLE: &raw::pokedex_callback_vtable = &raw::pokedex_callback_vtable {
        inst_fetch_2: Some(Self::inst_fetch_2),
        read_mem_1: Some(Self::read_mem_1),
        read_mem_2: Some(Self::read_mem_2),
        read_mem_4: Some(Self::read_mem_4),
        write_mem_1: Some(Self::write_mem_1),
        write_mem_2: Some(Self::write_mem_2),
        write_mem_4: Some(Self::write_mem_4),
        amo_mem_4: Some(Self::amo_mem_4),
        lr_mem_4: Some(Self::lr_mem_4),
        sc_mem_4: Some(Self::sc_mem_4),
        log_inst_issue: Some(Self::log_inst_issue),
        log_inst_issue_c: Some(Self::log_inst_issue_c),
        log_inst_commit: Some(Self::log_inst_commit),
        log_inst_xcpt: Some(Self::log_inst_xcpt),
        log_intr_taken: Some(Self::log_intr_taken),
        log_write_xreg: Some(Self::log_write_xreg),
        log_write_freg: Some(Self::log_write_freg),
        log_write_vreg: Some(Self::log_write_vreg),
        log_write_csr: Some(Self::log_write_csr),
        debug_print: Some(Self::debug_print),
    };
}

#[derive(Clone, Copy)]
pub struct VTable {
    // invariant: ABI version already checked
    data: &'static raw::pokedex_vtable,
}

fn assert_abi_version(vtable: &'static raw::pokedex_vtable) {
    // FIXME: it should be a direct CStr after bindgen generate_cstr issue fixed
    let abi_exe = CStr::from_bytes_with_nul(raw::POKEDEX_ABI_VERSION).unwrap();
    let abi_lib = unsafe { CStr::from_ptr(vtable.abi_version) };
    assert!(
        abi_exe == abi_lib,
        "ABI version mismatch, abi_exe=\"{abi_exe:?}\", abi_lib=\"{abi_lib:?}\""
    );
}

impl VTable {
    pub fn from_dylib(so_path: &str) -> Self {
        let so_path_cstr = CString::new(so_path).unwrap();
        let so_lib =
            unsafe { libc::dlopen(so_path_cstr.as_ptr(), libc::RTLD_NOW | libc::RTLD_LOCAL) };
        if so_lib.is_null() {
            let err = unsafe { CStr::from_ptr(libc::dlerror()) };
            panic!("dlopen failed: {err:?}")
        }

        info!("MODEL LIB using dylib: {so_path}");

        let entry_name = c"EXPORT_ASL_MODEL_get_pokedex_vtable";

        let entry = unsafe { libc::dlsym(so_lib, entry_name.as_ptr()) };
        let entry: raw::get_pokedex_vtable_t = unsafe { std::mem::transmute(entry) };

        let entry = entry.unwrap_or_else(|| panic!("dlsym failed: {entry_name:?} not found"));
        let vtable: &'static raw::pokedex_vtable = unsafe { &*entry() };

        assert_abi_version(vtable);

        Self { data: vtable }

        // no dlclose intentionally, we want vtable has static lifetime
    }

    #[cfg(feature = "bundled-model-lib")]
    pub fn bundled() -> Self {
        info!("MODEL LIB using statically-linked version");

        unsafe extern "C" {
            #[link_name = "EXPORT_ASL_MODEL_get_pokedex_vtable"]
            fn get_pokedex_vtable() -> *const raw::pokedex_vtable;
        }

        // Type check for the function signature
        let _: raw::get_pokedex_vtable_t = Some(get_pokedex_vtable);

        // Seems no need to check abi version in static linking
        // assert_abi_version(vtable);

        Self {
            data: unsafe { &*get_pokedex_vtable() },
        }
    }
}

// ModelRawHandle behaves like a raw pointer rather than an RAII wrapper
#[derive(Clone, Copy)]
pub struct ModelRawHandle {
    data: NonNull<c_void>,
    vtable: &'static raw::pokedex_vtable,
}

impl ModelRawHandle {
    pub unsafe fn new<C: PokedexCallback>(vtable: VTable, callback: &mut C) -> Self {
        let vtable = vtable.data;

        // ABI version check is already done in constructor
        // vtable.check_abi_version()

        let mut err_buf = [0u8; 256];
        let create_info = raw::pokedex_create_info {
            callback_data: callback as *mut C as *mut c_void,
            callback_vtable: MakeVTable::<C>::VTABLE,
        };
        let data = unsafe {
            (vtable.create.unwrap())(
                &create_info,
                err_buf.as_mut_ptr() as *mut c_char,
                err_buf.len(),
            )
        };

        let data = NonNull::new(data).unwrap_or_else(|| {
            let err = cstr_span(&err_buf);
            if err.is_empty() {
                error!("ASL model create error: (no error message)");
            } else {
                error!("ASL model create error: {}", err.escape_ascii());
            }
            panic!("ASL model creation failed");
        });

        Self { data, vtable }
    }
}

fn cstr_span(buf: &[u8]) -> &[u8] {
    match CStr::from_bytes_until_nul(buf) {
        Ok(buf) => buf.to_bytes(),
        Err(_) => buf,
    }
}

impl ModelRawHandle {
    // ModelHandle intentionally not implement Drop,
    // High level abstractions can precisely control drop order.
    //
    // SAFETY: After destroy, the handle is no longer valid
    pub unsafe fn destroy(self) {
        unsafe {
            (self.vtable.destroy.unwrap())(self.data.as_ptr());
        }
    }

    // SAFETY: callback_data is implicity borrowed
    pub unsafe fn reset(self, initial_pc: u32) {
        unsafe {
            (self.vtable.reset.unwrap())(self.data.as_ptr(), initial_pc);
        }
    }

    // SAFETY: callback_data is implicity borrowed
    pub unsafe fn step(self) {
        unsafe {
            (self.vtable.step.unwrap())(self.data.as_ptr());
        }
    }
}
