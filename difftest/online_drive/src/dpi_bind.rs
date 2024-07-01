#![allow(non_snake_case)]
#![allow(unused_variables)]

use std::ffi::{c_char, c_int, CString};
use std::ptr;
use log::info;

pub type SvScalar = u8;
pub type SvBit = SvScalar;
pub type SvBitVecVal = u32;

#[no_mangle]
fn axi_write_highBandwidthPort(
  channel_id: ::std::os::raw::c_longlong,
  awid: ::std::os::raw::c_longlong,
  awaddr: ::std::os::raw::c_longlong,
  awlen: ::std::os::raw::c_longlong,
  awsize: ::std::os::raw::c_longlong,
  awburst: ::std::os::raw::c_longlong,
  awlock: ::std::os::raw::c_longlong,
  awcache: ::std::os::raw::c_longlong,
  awprot: ::std::os::raw::c_longlong,
  awqos: ::std::os::raw::c_longlong,
  awregion: ::std::os::raw::c_longlong,
  payload: *const SvBitVecVal,
) {
  // TODO:
}

#[no_mangle]
fn axi_read_highBandwidthPort(
  channel_id: ::std::os::raw::c_longlong,
  arid: ::std::os::raw::c_longlong,
  araddr: ::std::os::raw::c_longlong,
  arlen: ::std::os::raw::c_longlong,
  arsize: ::std::os::raw::c_longlong,
  arburst: ::std::os::raw::c_longlong,
  arlock: ::std::os::raw::c_longlong,
  arcache: ::std::os::raw::c_longlong,
  arprot: ::std::os::raw::c_longlong,
  arqos: ::std::os::raw::c_longlong,
  arregion: ::std::os::raw::c_longlong,
  payload: *mut SvBitVecVal,
) {
  // TODO:
}

#[no_mangle]
extern "C" fn axi_read_indexedAccessPort(
  channel_id: ::std::os::raw::c_longlong,
  arid: ::std::os::raw::c_longlong,
  araddr: ::std::os::raw::c_longlong,
  arlen: ::std::os::raw::c_longlong,
  arsize: ::std::os::raw::c_longlong,
  arburst: ::std::os::raw::c_longlong,
  arlock: ::std::os::raw::c_longlong,
  arcache: ::std::os::raw::c_longlong,
  arprot: ::std::os::raw::c_longlong,
  arqos: ::std::os::raw::c_longlong,
  arregion: ::std::os::raw::c_longlong,
  payload: *mut SvBitVecVal,
) {
  // TODO:
}

#[no_mangle]
extern "C" fn axi_write_indexedAccessPort(
  channel_id: ::std::os::raw::c_longlong,
  awid: ::std::os::raw::c_longlong,
  awaddr: ::std::os::raw::c_longlong,
  awlen: ::std::os::raw::c_longlong,
  awsize: ::std::os::raw::c_longlong,
  awburst: ::std::os::raw::c_longlong,
  awlock: ::std::os::raw::c_longlong,
  awcache: ::std::os::raw::c_longlong,
  awprot: ::std::os::raw::c_longlong,
  awqos: ::std::os::raw::c_longlong,
  awregion: ::std::os::raw::c_longlong,
  payload: *const SvBitVecVal,
) {
  // TODO:
}

#[no_mangle]
extern "C" fn cosim_init(call_init: *mut SvBit) {
  info!("cosim_init");
}

#[no_mangle]
extern "C" fn cosim_watchdog(reason: *mut ::std::os::raw::c_char) {
  info!("cosim_watchdog");
}

#[no_mangle]
extern "C" fn issue_vector_instruction(issue: *mut SvBitVecVal) {
  info!("issue_vector_instruction");
}

extern "C" {
  fn verilator_main(
    argc: ::std::os::raw::c_int,
    argv: *mut *mut ::std::os::raw::c_char,
  ) -> ::std::os::raw::c_int;
}

pub(crate) fn verilator_main_wrapped() {
  let mut c_args_ptr: Vec<*mut c_char> = std::env::args()
      .collect::<Vec<String>>()
      .iter()
      .map(|arg| CString::new(arg.as_str()).expect("CString conversion failed"))
      .map(|arg| arg.as_ptr() as *mut c_char)
      .collect();

  c_args_ptr.push(ptr::null_mut());

  let argc = std::env::args().len() as c_int;

  let argv = c_args_ptr.as_mut_ptr();

  unsafe { verilator_main(argc, argv); }
}