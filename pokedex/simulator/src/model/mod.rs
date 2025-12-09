use std::{
    collections::HashSet,
    ffi::{CStr, c_char, c_void},
    ptr::NonNull,
};
use tracing::error;

use crate::{bus::AtomicOp, util::Bitmap32};

mod ffi;

pub use ffi::Loader;

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

#[derive(Debug, Default)]
pub struct Privileges {
    machine: bool,
    supervisor: bool,
    user: bool,
}

impl Privileges {
    pub fn new(p: &str) -> Self {
        assert!(p.len() <= 3);

        let mut privs = Privileges::default();
        p.chars().for_each(|c| match c {
            'M' => privs.machine = true,
            'S' => privs.supervisor = true,
            'U' => privs.user = true,
            _ => panic!("Unknown privilege {c}"),
        });

        privs
    }
}

pub struct ModelDesc {
    pub privs: Privileges,
    pub xlen: u8,
    pub vlen: u16,
    pub flen: u8,
    pub isa: &'static CStr,
}

impl ModelDesc {
    pub fn from_raw_model_desc(rmd: &ffi::raw::pokedex_model_description) -> Self {
        assert!(rmd.xlen == 32);
        assert!(rmd.flen == 0 || rmd.flen == 32);
        assert!(rmd.vlen % 8 == 0 && rmd.vlen < (u16::MAX as u32));

        let xlen = rmd.xlen as u8;
        let vlen = rmd.vlen as u16;
        let flen = rmd.flen as u8;
        let isa = unsafe { CStr::from_ptr(rmd.model_isa) };
        let privs_raw = unsafe { CStr::from_ptr(rmd.model_priv).to_string_lossy() };
        let privs = Privileges::new(&privs_raw);

        Self {
            privs,
            xlen,
            vlen,
            flen,
            isa,
        }
    }

    pub fn has_scalar_fp(&self) -> bool {
        self.flen != 0
    }
}

pub struct ModelHandle {
    data: NonNull<c_void>,
    vtable: &'static ffi::raw::pokedex_model_export,
    model_desc: ModelDesc,
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
    pub fn new(loader: Loader) -> Self {
        let vtable = loader.data;

        // ABI version check is already done in constructor
        // vtable.check_abi_version()

        let create_info = ffi::raw::pokedex_create_info {
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

        let desc: &ffi::raw::pokedex_model_description =
            unsafe { &*(vtable.get_description.unwrap())(data.as_ptr()) };
        let model_desc = ModelDesc::from_raw_model_desc(desc);

        Self {
            data,
            vtable,
            model_desc,
        }
    }
}

fn cstr_span(buf: &[u8]) -> &[u8] {
    match CStr::from_bytes_until_nul(buf) {
        Ok(buf) => buf.to_bytes(),
        Err(_) => buf,
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
                ffi::make_mem_vtable::<Mem>(),
                mem as *mut Mem as *mut c_void,
            )
        };
        ffi::code_from_raw(code)
    }

    pub fn step_trace<Mem: PokedexCallbackMem>(&mut self, mem: &mut Mem) -> StepDetail<'_> {
        let code = unsafe {
            (self.vtable.step.unwrap())(
                self.data.as_ptr(),
                ffi::make_mem_vtable::<Mem>(),
                mem as *mut Mem as *mut c_void,
            )
        };
        let tb = unsafe { &*(self.vtable.get_trace_buffer.unwrap())(self.data.as_ptr()) };

        assert_eq!(code, tb.step_status);
        // x0 should be modified
        assert!(tb.xreg_mask & 1 == 0);
        assert!((tb.csr_count as u32) < ffi::raw::POKEDEX_MAX_CSR_WRITE);
        let (code, inst) = ffi::detail_from_raw(code, tb.inst);
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
    tb: &'a ffi::raw::pokedex_trace_buffer,
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

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Inst {
    NC(u32),
    C(u16),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StepCode {
    Interrupt,
    Exception,
    Committed,
}

pub struct StepDetail<'a> {
    pub code: StepCode,
    pub pc: u32,
    pub inst: Option<Inst>,
    pub changes: CoreChange<'a>,
}

pub fn get_loader() -> anyhow::Result<Loader> {
    match std::env::var("POKEDEX_MODEL_DYLIB") {
        Ok(so_path) => Ok(Loader::from_dylib(&so_path)),
        Err(_) => {
            #[cfg(not(feature = "bundled-model-lib"))]
            {
                error!("env POKEDEX_MODEL_DYLIB not set");
                error!("pokedex is not compiled with a bundled model lib");
                anyhow::bail!("model lib not found");
            }

            #[cfg(feature = "bundled-model-lib")]
            {
                info!("env POKEDEX_MODEL_DYLIB not set, using bundled version");
                Ok(Loader::bundled())
            }
        }
    }
}
