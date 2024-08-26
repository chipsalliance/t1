// force link with online_dpi
extern crate online_dpi;

use std::{
  ffi::{c_char, c_int, CString},
  ptr,
};

fn main() {
  let c_args: Vec<CString> = std::env::args().map(|arg| CString::new(arg).unwrap()).collect();

  let mut c_args_ptr: Vec<*const c_char> = c_args.iter().map(|arg| arg.as_ptr()).collect();
  c_args_ptr.push(ptr::null());

  let argc = c_args.len() as c_int;
  let argv = c_args_ptr.as_ptr() as *mut *mut c_char;

  unsafe {
    verilator_main_c(argc, argv);
  }

  std::fs::write(
    "perf.txt",
    format!("total_cycles: {}", online_dpi::get_t()),
  )
  .expect("fail to write into perf.txt");
}

extern "C" {
  fn verilator_main_c(argc: c_int, argv: *mut *mut c_char) -> c_int;
}
