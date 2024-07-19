use std::{
    cell::RefCell,
    ffi::{c_void, CString},
    fmt::Display,
};

use fst_sys::*;

pub struct Writer {
    ptr: *mut c_void,
    time: u64,
}

impl Writer {
    pub fn new(path: &str) -> Result<Self, Error> {
        let path = CString::new(path).unwrap();
        let use_compress_hier = 1;
        let ptr = unsafe { fstWriterCreate(path.as_ptr(), use_compress_hier) };
        if ptr.is_null() {
            Err(Error::Unspecified)
        } else {
            Ok(Self { ptr, time: 0 })
        }
    }

    pub fn flush(&mut self) {
        unsafe {
            fstWriterFlushContext(self.ptr);
        }
    }

    pub fn set_time(&mut self, time: u64) {
        assert!(time > self.time);
        unsafe {
            fstWriterEmitTimeChange(self.ptr, time);
        }
        self.time = time;
    }
}

impl Drop for Writer {
    fn drop(&mut self) {
        unsafe {
            fstWriterClose(self.ptr);
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Error {
    Unspecified,
}

impl Display for Error {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("unspecified libfst error")
    }
}

impl std::error::Error for Error {}

pub type FstResult<T> = Result<T, Error>;

impl Writer {
    pub fn scope(&mut self, name: &str) -> Result<(), Error> {
        let name = CString::new(name).unwrap();
        unsafe {
            fstWriterSetScope(
                self.ptr,
                fstScopeType_FST_ST_VCD_SCOPE,
                name.as_ptr(),
                std::ptr::null(),
            );
        }
        Ok(())
    }

    pub fn upscope(&mut self) {
        unsafe {
            fstWriterSetUpscope(self.ptr);
        }
    }

    pub fn create_var_logic(&mut self, name: &str) -> Result<HandleLogic, Error> {
        let handle = self._create_var(name, fstVarType_FST_VT_VCD_REG, 1)?;
        Ok(HandleLogic { handle })
    }

    pub fn create_var_logic_bus(
        &mut self,
        name: &str,
        width: usize,
    ) -> Result<HandleLogicBus, Error> {
        const MAX_WIDTH: usize = 512;
        assert!(width > 0 && width <= MAX_WIDTH);
        let handle = self._create_var(name, fstVarType_FST_VT_VCD_REG, width as u32)?;
        Ok(HandleLogicBus {
            handle,
            buffer: RefCell::new(vec![b'0'; width].into_boxed_slice()),
        })
    }

    pub fn create_var_u8(&mut self, name: &str) -> Result<HandleLogicBus, Error> {
        self.create_var_logic_bus(name, 8)
    }

    pub fn create_var_u32(&mut self, name: &str) -> Result<HandleLogicBus, Error> {
        self.create_var_logic_bus(name, 32)
    }

    pub fn create_var_u64(&mut self, name: &str) -> Result<HandleLogicBus, Error> {
        self.create_var_logic_bus(name, 64)
    }

    pub fn create_var_string(&mut self, name: &str, max_len: usize) -> Result<HandleString, Error> {
        const MAX_LEN: usize = 64;
        assert!(max_len > 0 && max_len <= MAX_LEN);
        self.create_var_logic_bus(name, max_len * 8)
            .map(HandleString)
    }

    fn _create_var(
        &mut self,
        name: &str,
        fst_type: fstVarType,
        len: u32,
    ) -> Result<fstHandle, Error> {
        let name = CString::new(name).unwrap();
        let handle = unsafe {
            fstWriterCreateVar(
                self.ptr,
                fst_type,
                fstVarDir_FST_VD_BUFFER,
                len,
                name.as_ptr(),
                0,
            )
        };
        if handle != 0 {
            Ok(handle)
        } else {
            Err(Error::Unspecified)
        }
    }

    fn _emit_value_change(&mut self, handle: fstHandle, data: *const c_void) -> Result<(), Error> {
        unsafe {
            fstWriterEmitValueChange(self.ptr, handle, data);
        }
        Ok(())
    }
}
pub struct HandleLogic {
    handle: fstHandle,
}

impl HandleLogic {
    pub fn write_bool(&self, writer: &mut Writer, data: bool) -> Result<(), Error> {
        let data = if data { b"1" } else { b"0" };
        writer._emit_value_change(self.handle, data.as_ptr() as *const c_void)
    }

    pub fn write_x(&self, writer: &mut Writer) -> Result<(), Error> {
        writer._emit_value_change(self.handle, b"x".as_ptr() as *const c_void)
    }
}

pub struct HandleLogicBus {
    handle: fstHandle,
    buffer: RefCell<Box<[u8]>>,
}

impl HandleLogicBus {
    pub fn write_x(&self, writer: &mut Writer) -> Result<(), Error> {
        let buffer: &mut [u8] = &mut self.buffer.borrow_mut();
        buffer.fill(b'x');
        writer._emit_value_change(self.handle, buffer.as_ptr() as *const c_void)
    }

    pub fn write_u8(&self, writer: &mut Writer, data: u8) -> Result<(), Error> {
        self._write_data(writer, |buf| {
            assert!(buf.len() == 8);
            for (i, p) in buf.iter_mut().rev().enumerate() {
                *p = if (data & (1 << i)) != 0 { b'1' } else { b'0' };
            }
        })
    }

    pub fn write_u32(&self, writer: &mut Writer, data: u32) -> Result<(), Error> {
        self._write_data(writer, |buf| {
            assert!(buf.len() == 32);
            for (i, p) in buf.iter_mut().rev().enumerate() {
                *p = if (data & (1 << i)) != 0 { b'1' } else { b'0' };
            }
        })
    }

    pub fn write_u64(&self, writer: &mut Writer, data: u64) -> Result<(), Error> {
        self._write_data(writer, |buf| {
            assert!(buf.len() == 64);
            for (i, p) in buf.iter_mut().rev().enumerate() {
                *p = if (data & (1 << i)) != 0 { b'1' } else { b'0' };
            }
        })
    }

    fn _write_data(
        &self,
        writer: &mut Writer,
        fill_fn: impl FnOnce(&mut [u8]),
    ) -> Result<(), Error> {
        let buffer: &mut [u8] = &mut self.buffer.borrow_mut();
        fill_fn(buffer);
        writer._emit_value_change(self.handle, buffer.as_ptr() as *const c_void)
    }
}

pub struct HandleString(HandleLogicBus);

impl HandleString {
    pub fn write_str(&self, writer: &mut Writer, data: &str) -> Result<(), Error> {
        self.0._write_data(writer, |buf| {
            assert!(data.len() * 8 <= buf.len());

            let mut idx = buf.len();
            for c in data.bytes().rev() {
                idx -= 8;
                for i in 0..8 {
                    buf[idx + i] = if (c << i) & 0x80 != 0 { b'1' } else { b'0' };
                }
            }
            buf[..idx].fill(b'0');
        })
    }

    pub fn write_x(&self, writer: &mut Writer) -> Result<(), Error> {
        self.0.write_x(writer)
    }
}
