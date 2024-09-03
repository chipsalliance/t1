use std::{
  ffi::{c_void, CString},
  ptr::{self, NonNull},
};

#[rustfmt::skip]
pub mod sys;

/// get current simulation time in _simulation time unit_
#[cfg(feature = "sv2023")]
pub fn get_time() -> u64 {
  let mut time = sys::svTimeVal {
    type_: sys::sv_sim_time as i32,
    high: 0,
    low: 0,
    real: 0.0,
  };
  unsafe {
    let ret = sys::svGetTime(ptr::null_mut(), &mut time);
    assert!(ret == 0, "svGetTime failed");
  }

  ((time.high as u64) << 32) + (time.low as u64)
}

pub fn set_scope_by_name(name: &str) {
  let name_cstr = CString::new(name).unwrap();
  unsafe {
    let scope = sys::svGetScopeFromName(name_cstr.as_ptr());
    assert!(!scope.is_null(), "unrecognized scope `{name}`");
    sys::svSetScope(scope);
  }
}

pub fn set_scope(scope: SvScope) {
  unsafe {
    sys::svSetScope(scope.ptr.as_ptr());
  }
}

#[derive(Debug, Clone, Copy)]
pub struct SvScope {
  ptr: NonNull<c_void>,
}

unsafe impl Send for SvScope {}

impl SvScope {
  pub fn get_current() -> Option<Self> {
    let ptr = unsafe { sys::svGetScope() };
    NonNull::new(ptr).map(|ptr| Self { ptr })
  }
}
