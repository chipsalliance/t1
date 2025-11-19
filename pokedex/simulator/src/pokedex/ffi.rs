use std::{
    ffi::{CStr, CString, c_char, c_int, c_void},
    marker::PhantomData,
    ptr::NonNull,
};
use tracing::{error, info};

use crate::{
    pokedex::simulator::{StepCode, StepDetail},
    util::Bitmap32,
};

use super::{bus::AtomicOp, simulator::Inst};

#[allow(nonstandard_style)]
mod raw {
    include!(concat!(env!("OUT_DIR"), "/pokedex_interface.rs"));
}

// See include/pokedex_interface.h for more
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

struct MakeVTable<T: PokedexCallbackMem>(PhantomData<T>);

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

impl<T: PokedexCallbackMem> MakeVTable<T> {
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

#[derive(Clone, Copy)]
pub struct VTable {
    // invariant: ABI version already checked
    data: &'static raw::pokedex_model_export,
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

pub struct ModelHandle {
    data: NonNull<c_void>,
    vtable: &'static raw::pokedex_model_export,
}

impl Drop for ModelHandle {
    fn drop(&mut self) {
        unsafe {
            (self.vtable.destroy.unwrap())(self.data.as_ptr());
        }
    }
}

unsafe extern "C" fn model_debug_log(message: *const c_char) {
    let message = unsafe { CStr::from_ptr(message) };
    tracing::debug!("ASL model: {message:?}");
}

impl ModelHandle {
    pub unsafe fn new(vtable: VTable) -> Self {
        let vtable = vtable.data;

        // ABI version check is already done in constructor
        // vtable.check_abi_version()

        let create_info = raw::pokedex_create_info {
            debug_log: Some(model_debug_log),
            debug_inst_issue: 0,
        };

        let mut err_buf = [0u8; 256];
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

fn code_from_raw(code: u8) -> StepCode {
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

fn detail_from_raw(code: u8, inst: u32) -> (StepCode, Option<Inst>) {
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

impl ModelHandle {
    pub fn read_pc(&self) -> u32 {
        let mut pc: u64 = 0;
        unsafe {
            (self.vtable.get_pc.unwrap())(self.data.as_ptr(), &mut pc);
        }
        pc as u32
    }
    pub fn read_xreg(&self, idx: u8) -> u32 {
        let mut value = 0;
        unsafe {
            (self.vtable.get_xreg.unwrap())(self.data.as_ptr(), idx, &mut value);
        }
        value as u32
    }
    pub fn read_freg(&self, idx: u8) -> u32 {
        let mut value: u64 = 0;
        unsafe {
            (self.vtable.get_freg.unwrap())(self.data.as_ptr(), idx, &mut value);
        }
        value as u32
    }
    pub fn read_vreg(&self, idx: u8, value: &mut [u8]) {
        unsafe {
            (self.vtable.get_vreg.unwrap())(
                self.data.as_ptr(),
                idx,
                value.as_mut_ptr(),
                value.len(),
            );
        }
    }
    pub fn read_csr(&self, idx: u16) -> u32 {
        let mut value: u64 = 0;
        unsafe {
            (self.vtable.get_csr.unwrap())(self.data.as_ptr(), idx, &mut value);
        }
        value as u32
    }
}

impl ModelHandle {
    pub fn reset(&mut self, initial_pc: u32) {
        unsafe {
            (self.vtable.reset.unwrap())(self.data.as_ptr(), initial_pc);
        }
    }

    pub fn step<Mem: PokedexCallbackMem>(&mut self, mem: &mut Mem) -> StepCode {
        let code = unsafe {
            (self.vtable.step.unwrap())(
                self.data.as_ptr(),
                MakeVTable::<Mem>::VTABLE,
                mem as *mut Mem as *mut c_void,
            )
        };
        code_from_raw(code)
    }

    pub fn step_trace<Mem: PokedexCallbackMem>(&mut self, mem: &mut Mem) -> StepDetail<'_> {
        let code = unsafe {
            (self.vtable.step.unwrap())(
                self.data.as_ptr(),
                MakeVTable::<Mem>::VTABLE,
                mem as *mut Mem as *mut c_void,
            )
        };
        let tb = unsafe { &*(self.vtable.get_trace_buffer.unwrap())(self.data.as_ptr()) };

        assert_eq!(code, tb.step_status);
        // x0 should be modified
        assert!(tb.xreg_mask & 1 == 0);
        assert!((tb.csr_count as u32) < raw::POKEDEX_MAX_CSR_WRITE);
        let (code, inst) = detail_from_raw(code, tb.inst);
        StepDetail {
            code,
            pc: tb.pc,
            inst,
            changes: CoreChange { core: self, tb },
        }
    }
}

#[derive(Clone, Copy)]
pub struct CoreChange<'a> {
    pub core: &'a ModelHandle,
    tb: &'a raw::pokedex_trace_buffer,
}

impl CoreChange<'_> {
    pub fn xreg_change_indices(self) -> impl Iterator<Item = u8> {
        Bitmap32::from_mask(self.tb.xreg_mask)
            .indices()
            .map(|x| x as u8)
    }
    pub fn freg_change_indices(self) -> impl Iterator<Item = u8> {
        Bitmap32::from_mask(self.tb.freg_mask)
            .indices()
            .map(|x| x as u8)
    }
    pub fn vreg_change_indices(self) -> impl Iterator<Item = u8> {
        Bitmap32::from_mask(self.tb.vreg_mask)
            .indices()
            .map(|x| x as u8)
    }
    pub fn csr_change_indices(self) -> impl Iterator<Item = u16> {
        self.tb.csr_indices[..self.tb.csr_count as usize]
            .iter()
            .copied()
    }

    pub fn xreg_changes(self) -> impl Iterator<Item = (u8, u32)> {
        let core = self.core;
        self.xreg_change_indices().map(|x| (x, core.read_xreg(x)))
    }

    pub fn freg_changes(self) -> impl Iterator<Item = (u8, u32)> {
        let core = self.core;
        self.freg_change_indices().map(|x| (x, core.read_freg(x)))
    }

    pub fn csr_changes(self) -> impl Iterator<Item = (u16, u32)> {
        let core = self.core;
        self.csr_change_indices().map(|x| (x, core.read_csr(x)))
    }

    pub fn is_empty_changes(self) -> bool {
        let tb = self.tb;
        tb.xreg_mask == 0 && tb.freg_mask == 0 && tb.vreg_mask == 0 && tb.csr_count == 0
    }
}
