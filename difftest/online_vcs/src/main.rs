// force link with online_dpi
extern crate online_dpi;

use std::{
  ffi::{c_char, c_int, c_void, CString},
  ptr,
};

fn main() {
  let c_args: Vec<CString> = std::env::args().map(|arg| CString::new(arg).unwrap()).collect();

  let mut c_args_ptr: Vec<*const c_char> = c_args.iter().map(|arg| arg.as_ptr()).collect();
  c_args_ptr.push(ptr::null());

  let argc = c_args.len() as c_int;
  let argv = c_args_ptr.as_ptr() as *mut *mut c_char;

  unsafe {
    vcs_main(argc, argv);
    VcsInit();
    VcsSimUntil(1<<31);
  }
}

extern "C" {
  fn vcs_main(argc: c_int, argv: *mut *mut c_char) -> c_int;
  fn VcsInit() -> c_void;
  fn VcsSimUntil(c: c_int) -> c_void;
}

