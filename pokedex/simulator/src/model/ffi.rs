use std::ffi::{CStr, CString, c_int, c_void};
use std::marker::PhantomData;

use tracing::info;

use super::{Inst, StepCode};
use crate::bus::AtomicOp;

#[allow(nonstandard_style)]
pub(super) mod raw {
    include!(concat!(env!("OUT_DIR"), "/pokedex_interface.rs"));
}

struct MakeVTable<T: super::PokedexCallbackMem>(PhantomData<T>);

trait ResultExt1 {
    fn to_c_int(self) -> c_int;
}
trait ResultExt2<T> {
    fn try_write(self, ret: *mut T) -> c_int;
}

impl<E> ResultExt1 for Result<(), E> {
    fn to_c_int(self) -> c_int {
        match self {
            Ok(()) => 0,
            Err(_e) => 1,
        }
    }
}

impl<E, T: Copy> ResultExt2<T> for Result<T, E> {
    fn try_write(self, ret: *mut T) -> c_int {
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

impl<T: super::PokedexCallbackMem> MakeVTable<T> {
    unsafe extern "C" fn inst_fetch_2(model: *mut c_void, addr: u32, ret: *mut u16) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.inst_fetch_2(addr).try_write(ret)
    }
    unsafe extern "C" fn read_mem_1(model: *mut c_void, addr: u32, ret: *mut u8) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.read_mem_u8(addr).try_write(ret)
    }
    unsafe extern "C" fn read_mem_2(model: *mut c_void, addr: u32, ret: *mut u16) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.read_mem_u16(addr).try_write(ret)
    }
    unsafe extern "C" fn read_mem_4(model: *mut c_void, addr: u32, ret: *mut u32) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.read_mem_u32(addr).try_write(ret)
    }
    unsafe extern "C" fn write_mem_1(model: *mut c_void, addr: u32, value: u8) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.write_mem_u8(addr, value).to_c_int()
    }
    unsafe extern "C" fn write_mem_2(model: *mut c_void, addr: u32, value: u16) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.write_mem_u16(addr, value).to_c_int()
    }
    unsafe extern "C" fn write_mem_4(model: *mut c_void, addr: u32, value: u32) -> c_int {
        let model = unsafe { &mut *(model as *mut T) };
        model.write_mem_u32(addr, value).to_c_int()
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
        model.amo_mem_u32(addr, op, value).try_write(ret)
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
    const VTABLE: &raw::pokedex_mem_callback_vtable = &raw::pokedex_mem_callback_vtable {
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
    };
}

pub(super) fn make_mem_vtable<T: super::PokedexCallbackMem>()
-> &'static raw::pokedex_mem_callback_vtable {
    MakeVTable::<T>::VTABLE
}

#[derive(Clone, Copy)]
pub struct Loader {
    // invariant: ABI version already checked
    pub(super) data: &'static raw::pokedex_model_export,
}

fn assert_abi_version(vtable: &'static raw::pokedex_model_export) {
    // FIXME: it should be a direct CStr after bindgen generate_cstr issue fixed
    let abi_exe = CStr::from_bytes_with_nul(raw::POKEDEX_ABI_VERSION).unwrap();
    let abi_lib = unsafe { CStr::from_ptr(vtable.abi_version) };
    assert!(
        abi_exe == abi_lib,
        "ABI version mismatch, abi_exe={abi_exe:?}, abi_lib={abi_lib:?}"
    );
}

impl Loader {
    pub fn from_dylib(so_path: &str) -> Self {
        let so_path_cstr = CString::new(so_path).unwrap();
        let so_lib =
            unsafe { libc::dlopen(so_path_cstr.as_ptr(), libc::RTLD_NOW | libc::RTLD_LOCAL) };
        if so_lib.is_null() {
            let err = unsafe { CStr::from_ptr(libc::dlerror()) };
            panic!("dlopen failed: {err:?}")
        }

        info!("MODEL LIB using dylib: {so_path}");

        let entry_name = c"EXPORT_pokedex_get_model_export";

        let entry = unsafe { libc::dlsym(so_lib, entry_name.as_ptr()) };
        let entry: raw::pokedex_get_model_export_t = unsafe { std::mem::transmute(entry) };

        let entry = entry.unwrap_or_else(|| panic!("dlsym failed: {entry_name:?} not found"));
        let vtable: &'static raw::pokedex_model_export = unsafe { &*entry() };

        assert_abi_version(vtable);

        Self { data: vtable }

        // no dlclose intentionally, we want vtable has static lifetime
    }

    #[cfg(feature = "bundled-model-lib")]
    pub fn bundled() -> Self {
        info!("MODEL LIB using statically-linked version");

        unsafe extern "C" {
            #[link_name = "EXPORT_pokedex_get_model_export"]
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

pub(super) fn code_from_raw(code: u8) -> StepCode {
    use StepCode::*;
    match code as u32 {
        raw::POKEDEX_STEP_RESULT_INTERRUPT => Interrupt,
        raw::POKEDEX_STEP_RESULT_FETCH_XCPT
        | raw::POKEDEX_STEP_RESULT_INST_EXCEPTION
        | raw::POKEDEX_STEP_RESULT_INST_C_EXCEPTION => Exception,
        raw::POKEDEX_STEP_RESULT_INST_COMMIT | raw::POKEDEX_STEP_RESULT_INST_C_COMMIT => Committed,
        _ => unreachable!("unexpected step return value ({code})"),
    }
}

pub(super) fn detail_from_raw(code: u8, inst: u32) -> (StepCode, Option<Inst>) {
    use StepCode::*;
    match code as u32 {
        raw::POKEDEX_STEP_RESULT_INTERRUPT => (Interrupt, None),
        raw::POKEDEX_STEP_RESULT_FETCH_XCPT => (Exception, None),
        raw::POKEDEX_STEP_RESULT_INST_EXCEPTION => (Exception, Some(Inst::NC(inst))),
        raw::POKEDEX_STEP_RESULT_INST_C_EXCEPTION => (Exception, Some(Inst::C(inst as u16))),
        raw::POKEDEX_STEP_RESULT_INST_COMMIT => (Committed, Some(Inst::NC(inst))),
        raw::POKEDEX_STEP_RESULT_INST_C_COMMIT => (Committed, Some(Inst::C(inst as u16))),
        _ => unreachable!("unexpected step return value ({code})"),
    }
}
