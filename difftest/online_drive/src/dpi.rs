#![allow(non_snake_case)]
#![allow(unused_variables)]

use clap::Parser;
use std::ffi::{c_char, c_int, c_longlong, CString};
use std::ptr;
use tracing::debug;

use crate::drive::Driver;
use crate::OfflineArgs;

pub type SvBitVecVal = u32;

// --------------------------
// preparing data structures
// --------------------------

pub(crate) struct AxiReadPayload {
  pub(crate) data: Vec<u8>,
}

fn write_to_pointer(dst: *mut u8, data: &Vec<u8>, n: usize) {
  unsafe {
    for i in 0..n {
      ptr::write(dst.add(i), data[i]);
    }
  }
}

unsafe fn fill_axi_read_payload(dst: *mut SvBitVecVal, dlen: u32, payload: &AxiReadPayload) {
  let data_len = 256 * (dlen / 8) as usize;
  assert!(payload.data.len() <= data_len);
  let dst = dst as *mut u8;
  write_to_pointer(dst, &payload.data, payload.data.len());
}

// Return (strobe in bit, data in byte)
unsafe fn load_from_payload(
  payload: &*const SvBitVecVal,
  aw_size: c_longlong,
  data_width: u32,
) -> (Vec<bool>, &[u8]) {
  let src = *payload as *mut u8;
  let data_width_in_byte = (data_width / 8) as usize;
  let strb_width_in_byte = data_width_in_byte.div_ceil(8); // ceil divide by 8 to get byte width
  let payload_size_in_byte = strb_width_in_byte + data_width_in_byte; // data width in byte
  let byte_vec = std::slice::from_raw_parts(src, payload_size_in_byte);
  let strobe = &byte_vec[0..strb_width_in_byte];
  let data = &byte_vec[strb_width_in_byte..];

  let strb_width_in_bit = std::cmp::min(8, data_width_in_byte);
  let mut masks: Vec<bool> = strobe
    .into_iter()
    .flat_map(|strb| {
      let mask: Vec<bool> = (0..strb_width_in_bit).map(|i| (strb & (1 << i)) != 0).collect();
      mask
    })
    .collect();
  assert!(
    masks.len() == data.len(),
    "strobe bit width is not aligned with data byte width"
  );

  debug!(
    "load {payload_size_in_byte} byte from payload: raw_data={} strb={} data={}",
    hex::encode(byte_vec),
    hex::encode(strobe),
    hex::encode(data),
  );

  (masks, data)
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

pub static ISSUE_NOT_VALID: u32 = 0;
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
  debug!(
    "axi_write_highBandwidth (channel_id={channel_id}, awid={awid}, awaddr={awaddr:#x}, \
  awlen={awlen}, awsize={awsize}, awburst={awburst}, awlock={awlock}, awcache={awcache}, \
  awprot={awprot}, awqos={awqos}, awregion={awregion})"
  );
  let driver = &mut *(target as *mut Driver);
  let (strobe, data) = load_from_payload(&payload, awsize, driver.dlen);
  driver.axi_write_high_bandwidth(awaddr as u32, awsize as u64, &strobe, data);
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
  debug!(
    "axi_read_highBandwidth (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  let driver = &mut *(target as *mut Driver);
  let response = driver.axi_read_high_bandwidth(araddr as u32, arsize as u64);
  fill_axi_read_payload(payload, driver.dlen, &response);
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
  debug!(
    "axi_read_indexed (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  let driver = &mut *(target as *mut Driver);
  let response = driver.axi_read_indexed(araddr as u32, arsize as u64);
  fill_axi_read_payload(payload, driver.dlen, &response);
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
  debug!(
    "axi_write_indexed (channel_id={channel_id}, awid={awid}, awaddr={awaddr:#x}, \
  awlen={awlen}, awsize={awsize}, awburst={awburst}, awlock={awlock}, awcache={awcache}, \
  awprot={awprot}, awqos={awqos}, awregion={awregion})"
  );
  let driver = &mut *(target as *mut Driver);
  let (strobe, data) = load_from_payload(&payload, awsize, 32);
  driver.axi_write_indexed_access_port(awaddr as u32, awsize as u64, &strobe, data);
}

#[no_mangle]
unsafe extern "C" fn cosim_init_rs() -> *mut () {
  let args = OfflineArgs::parse();
  args.common_args.setup_logger().unwrap();

  let driver = Box::new(Driver::new(&args));
  Box::into_raw(driver) as *mut ()
}

#[no_mangle]
unsafe extern "C" fn cosim_watchdog_rs(target: *mut (), reason: *mut c_char) {
  // watchdog dpi call would be called before initialization, guard on null target
  if !target.is_null() {
    let driver = &mut *(target as *mut Driver);
    *reason = driver.watchdog() as c_char
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

#[no_mangle]
unsafe extern "C" fn retire_vector_mem_rs(target: *mut ()) {
  let driver = &mut *(target as *mut Driver);
  driver.retire_memory();
}

//--------------------------------
// import functions and wrappers
//--------------------------------

#[link(name = "dpi_pre_link")]
extern "C" {
  fn verilator_main_c(argc: c_int, argv: *mut *mut c_char) -> c_int;

  #[cfg(feature = "trace")]
  fn dump_wave_c(path: *const c_char);

  fn get_t_c() -> u64;
}

pub(crate) fn get_t() -> u64 {
  unsafe { get_t_c() / 20 }
}

pub(crate) fn verilator_main() {
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
    verilator_main_c(argc, argv);
  }

  std::fs::write("perf.txt", format!("total_cycles: {}", get_t()))
    .expect("fail to write into perf.txt");
}

#[cfg(feature = "trace")]
pub(crate) fn dump_wave(path: &str) {
  let path_cstring = CString::new(path).unwrap();
  let path_ptr: *const c_char = path_cstring.as_ptr();
  unsafe {
    dump_wave_c(path_ptr);
  }
}
