use std::{ffi::CString, ptr};

pub mod sys;

/// get current simulation time in _simulation time unit_
pub fn get_time() -> u64 {
  let mut time = sys::svTimeVal { type_: 0, high: 0, low: 0, real: 0.0 };
  unsafe {
    sys::svGetTime(ptr::null_mut(), &mut time);
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
