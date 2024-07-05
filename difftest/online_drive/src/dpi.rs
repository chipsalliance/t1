#![allow(non_snake_case)]
#![allow(unused_variables)]

use clap::Parser;
use std::ffi::{c_char, c_int, c_longlong, CString};
use std::ptr;

use tracing::{info, warn};

use crate::drive::Driver;
use crate::OfflineArgs;

pub type SvScalar = u8;
pub type SvBit = SvScalar;
pub type SvBitVecVal = u32;

// --------------------------
// preparing data structures
// --------------------------

pub(crate) struct AxiWritePayload {
  pub(crate) data: Vec<u8>,
  pub(crate) strb: Vec<u8>,
}

pub(crate) struct AxiReadPayload {
  pub(crate) data: Vec<u8>,
  pub(crate) beats: u8,
}

pub(crate) struct AxiReadIndexedPayload {
  pub(crate) data: [u8; 256 * 4],
  pub(crate) beats: u8,
}

pub(crate) struct AxiWriteIndexedPayload {
  pub(crate) data: [u8; 256 * 4],
  pub(crate) beats: [u8; 128],
}

fn read_from_pointer(src: *const u8, n: usize) -> Vec<u8> {
  let mut result = Vec::with_capacity(n);
  unsafe {
    for i in 0..n {
      result.push(ptr::read(src.add(i)));
    }
  }
  result
}

fn write_to_pointer(dst: *mut u8, data: &Vec<u8>, n: usize) {
  unsafe {
    for i in 0..n {
      ptr::write(dst.add(i), data[i]);
    }
  }
}

unsafe fn to_axi_write_payload(src: *const SvBitVecVal, dlen: u32) -> AxiWritePayload {
  let data_len = (256 / 8) * dlen as usize;
  let strb_len = data_len / 8;
  let src = src as *const u8;
  AxiWritePayload {
    data: read_from_pointer(src, data_len),
    strb: read_from_pointer(src.offset(data_len as isize), strb_len),
  }
}

unsafe fn fill_axi_read_payload(dst: *mut SvBitVecVal, dlen: u32, payload: AxiReadPayload) {
  let data_len = (256 / 8) * dlen as usize;
  let dst = dst as *mut u8;
  write_to_pointer(dst, &payload.data, data_len);
  ptr::write(dst.offset(data_len as isize), payload.beats);
}

#[repr(C, packed)]
#[derive(Default)]
pub(crate) struct IssueData {
  pub meta: u32,
  pub vcsr: u32,
  pub vstart: u32,
  pub vl: u32,
  pub vtype: u32,
  pub src2_bits: u32,
  pub src1_bits: u32,
  pub instruction_bits: u32,
}

pub static ISSUE_VALID: u32 = 1;
pub static ISSUE_FENCE: u32 = 2;
pub static ISSUE_EXIT: u32 = 3;

pub static WATCHDOG_CONTINUE: u8 = 0;
pub static WATCHDOG_TIMEOUT: u8 = 1;

#[repr(C, packed)]
pub(crate) struct Retire {
  pub vxsat: u32,
  pub write_rd: u32,
  pub data: u32,
  pub rd: u32,
}

//----------------------
// dpi functions
//----------------------

#[no_mangle]
unsafe extern "C" fn axi_write_highBandwidthPort_rs(
  target: *mut (),
  channel_id: c_longlong,
  awid: c_longlong,
  awaddr: c_longlong,
  awlen: c_longlong,
  awsize: c_longlong,
  awburst: c_longlong,
  awlock: c_longlong,
  awcache: c_longlong,
  awprot: c_longlong,
  awqos: c_longlong,
  awregion: c_longlong,
  payload: *const SvBitVecVal,
) {
  // TODO:
  let driver = &mut *(target as *mut Driver);
  let payload = to_axi_write_payload(payload, driver.dlen);
  driver.axi_write_high_bandwidth(&payload);
}

#[no_mangle]
unsafe extern "C" fn axi_read_highBandwidthPort_rs(
  target: *mut (),
  channel_id: c_longlong,
  arid: c_longlong,
  araddr: c_longlong,
  arlen: c_longlong,
  arsize: c_longlong,
  arburst: c_longlong,
  arlock: c_longlong,
  arcache: c_longlong,
  arprot: c_longlong,
  arqos: c_longlong,
  arregion: c_longlong,
  payload: *mut SvBitVecVal,
) {
  let driver = &mut *(target as *mut Driver);
  let response = driver.axi_read_high_bandwidth();
  fill_axi_read_payload(payload, driver.dlen, response);
}

#[no_mangle]
unsafe extern "C" fn axi_read_indexedAccessPort_rs(
  target: *mut (),
  channel_id: c_longlong,
  arid: c_longlong,
  araddr: c_longlong,
  arlen: c_longlong,
  arsize: c_longlong,
  arburst: c_longlong,
  arlock: c_longlong,
  arcache: c_longlong,
  arprot: c_longlong,
  arqos: c_longlong,
  arregion: c_longlong,
  payload: *mut SvBitVecVal,
) {
  let driver = &mut *(target as *mut Driver);
  *(payload as *mut AxiReadIndexedPayload) = driver.axi_read_indexed();
}

#[no_mangle]
unsafe extern "C" fn axi_write_indexedAccessPort_rs(
  target: *mut (),
  channel_id: c_longlong,
  awid: c_longlong,
  awaddr: c_longlong,
  awlen: c_longlong,
  awsize: c_longlong,
  awburst: c_longlong,
  awlock: c_longlong,
  awcache: c_longlong,
  awprot: c_longlong,
  awqos: c_longlong,
  awregion: c_longlong,
  payload: *const SvBitVecVal,
) {
  let driver = &mut *(target as *mut Driver);
  let payload = &*(payload as *const AxiWriteIndexedPayload);
  driver.axi_write_indexed(payload);
}

#[no_mangle]
unsafe extern "C" fn cosim_init_rs(call_init: *mut SvBit) -> *mut () {
  let args = OfflineArgs::parse();
  args.common_args.setup_logger().unwrap();

  *call_init = 1;
  init_wave();
  let driver = Box::new(Driver::new(&args));
  Box::into_raw(driver) as *mut ()
}

#[no_mangle]
unsafe extern "C" fn cosim_watchdog_rs(target: *mut (), reason: *mut c_char) {
  // watchdog dpi call would be called before initialization, guard on null target
  if !target.is_null() {
    info!("watchdog");
    let driver = &mut *(target as *mut Driver);
    *reason = driver.watchdog() as c_char
  } else {
    warn!("null target")
  }
}

#[no_mangle]
unsafe extern "C" fn issue_vector_instruction_rs(target: *mut (), issue_dst: *mut SvBitVecVal) {
  let driver = &mut *(target as *mut Driver);
  let issue = driver.issue_instruction();
  *(issue_dst as *mut IssueData) = issue;
}

#[no_mangle]
unsafe extern "C" fn retire_vector_instruction_rs(target: *mut (), retire_src: *const SvBitVecVal) {
  let driver = &mut *(target as *mut Driver);
  let retire = &*(retire_src as *const Retire);
  driver.retire_instruction(retire)
}

extern "C" {
  fn verilator_main(argc: c_int, argv: *mut *mut c_char) -> c_int;

  fn dump_wave(path: *const c_char);

  fn init_wave();
}

pub(crate) fn verilator_main_wrapped() {
  let mut c_args_ptr: Vec<*mut c_char> = std::env::args()
    .collect::<Vec<String>>()
    .iter()
    .map(|arg| CString::new(arg.as_str()).unwrap())
    .map(|arg| arg.as_ptr() as *mut c_char)
    .collect();

  c_args_ptr.push(ptr::null_mut());

  let argc = std::env::args().len() as c_int;

  let argv = c_args_ptr.as_mut_ptr();

  unsafe {
    verilator_main(argc, argv);
  }
}

pub(crate) fn dump_wave_wrapped(path: &str) {
  let path_cstring = CString::new(path).unwrap();
  let path_ptr:  *const c_char = path_cstring.as_ptr();
  unsafe {
    dump_wave(path_ptr);
  }
}