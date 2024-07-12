#![allow(non_snake_case)]
#![allow(unused_variables)]

use clap::Parser;
use std::ffi::{c_char, c_longlong};
use std::sync::Mutex;
use tracing::debug;

use crate::drive::Driver;
use crate::svdpi::SvScope;
use crate::OfflineArgs;

pub type SvBitVecVal = u32;

// --------------------------
// preparing data structures
// --------------------------

static DPI_TARGET: Mutex<Option<Box<Driver>>> = Mutex::new(None);

pub(crate) struct AxiReadPayload {
  pub(crate) data: Vec<u8>,
}

unsafe fn write_to_pointer(dst: *mut u8, data: &[u8]) {
  let dst = std::slice::from_raw_parts_mut(dst, data.len());
  dst.copy_from_slice(data);
}

unsafe fn fill_axi_read_payload(dst: *mut SvBitVecVal, dlen: u32, payload: &AxiReadPayload) {
  let data_len = 256 * (dlen / 8) as usize;
  assert!(payload.data.len() <= data_len);
  write_to_pointer(dst as *mut u8, &payload.data);
}

// Return (strobe in bit, data in byte)
unsafe fn load_from_payload<'a>(
  payload: *const SvBitVecVal,
  data_width: u32,
) -> (Vec<bool>, &'a [u8]) {
  let src = payload as *mut u8;
  let data_width_in_byte = (data_width / 8) as usize;
  let strb_width_in_byte = data_width_in_byte.div_ceil(8); // ceil divide by 8 to get byte width
  let payload_size_in_byte = strb_width_in_byte + data_width_in_byte; // data width in byte
  let byte_vec = std::slice::from_raw_parts(src, payload_size_in_byte);
  let strobe = &byte_vec[0..strb_width_in_byte];
  let data = &byte_vec[strb_width_in_byte..];

  let strb_width_in_bit = std::cmp::min(8, data_width_in_byte);
  let masks: Vec<bool> = strobe
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

/// evaluate after AW and W is finished at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_write_highBandwidthPort(
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
  // struct packed {bit [255:0][DLEN:0] data;
  // bit [255:0][DLEN/8:0] strb; } payload
  payload: *const SvBitVecVal,
) {
  debug!(
    "axi_write_highBandwidth (channel_id={channel_id}, awid={awid}, awaddr={awaddr:#x}, \
  awlen={awlen}, awsize={awsize}, awburst={awburst}, awlock={awlock}, awcache={awcache}, \
  awprot={awprot}, awqos={awqos}, awregion={awregion})"
  );
  let mut driver = DPI_TARGET.lock().unwrap();
  let driver = driver.as_mut().unwrap();
  let (strobe, data) = load_from_payload(payload, driver.dlen);
  driver.axi_write_high_bandwidth(awaddr as u32, awsize as u64, &strobe, data);
}

/// evaluate at AR fire at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_read_highBandwidthPort(
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
  // struct packed {bit [255:0][DLEN:0] data; byte beats; } payload
  payload: *mut SvBitVecVal,
) {
  debug!(
    "axi_read_highBandwidth (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  let mut driver = DPI_TARGET.lock().unwrap();
  let driver = driver.as_mut().unwrap();
  let response = driver.axi_read_high_bandwidth(araddr as u32, arsize as u64);
  fill_axi_read_payload(payload, driver.dlen, &response);
}

/// evaluate at AR fire at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_read_indexedAccessPort(
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
  // struct packed {bit [255:0][DLEN:0] data; byte beats; } payload
  payload: *mut SvBitVecVal,
) {
  debug!(
    "axi_read_indexed (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  let mut driver = DPI_TARGET.lock().unwrap();
  let driver = driver.as_mut().unwrap();
  let response = driver.axi_read_indexed(araddr as u32, arsize as u64);
  fill_axi_read_payload(payload, driver.dlen, &response);
}

/// evaluate after AW and W is finished at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_write_indexedAccessPort(
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
  // struct packed {bit [255:0][32:0] data; bit [255:0][4:0] strb; } payload
  payload: *const SvBitVecVal,
) {
  debug!(
    "axi_write_indexed (channel_id={channel_id}, awid={awid}, awaddr={awaddr:#x}, \
  awlen={awlen}, awsize={awsize}, awburst={awburst}, awlock={awlock}, awcache={awcache}, \
  awprot={awprot}, awqos={awqos}, awregion={awregion})"
  );
  let mut driver = DPI_TARGET.lock().unwrap();
  let driver = driver.as_mut().unwrap();
  let (strobe, data) = load_from_payload(payload, 32);
  driver.axi_write_indexed_access_port(awaddr as u32, awsize as u64, &strobe, data);
}

#[no_mangle]
unsafe extern "C" fn t1_cosim_init() {
  let args = OfflineArgs::parse();
  args.common_args.setup_logger().unwrap();

  let scope = SvScope::get_current().expect("failed to get scope in t1_cosim_init");

  let driver = Box::new(Driver::new(scope, &args));
  let mut dpi_target = DPI_TARGET.lock().unwrap();
  assert!(
    dpi_target.is_none(),
    "t1_cosim_init should be called only once"
  );
  *dpi_target = Some(driver);
}

/// evaluate at every 1024 cycles, return reason = 0 to continue simulation,
/// other value is used as error code.
#[no_mangle]
unsafe extern "C" fn cosim_watchdog(reason: *mut c_char) {
  // watchdog dpi call would be called before initialization, guard on null target
  let mut driver = DPI_TARGET.lock().unwrap();
  if let Some(driver) = driver.as_mut() {
    *reason = driver.watchdog() as c_char
  }
}

/// evaluate at instruction queue is not empty
/// arg issue will be type cast from a struct to svBitVecVal*(uint32_t*)
#[no_mangle]
unsafe extern "C" fn issue_vector_instruction(issue_dst: *mut SvBitVecVal) {
  let mut driver = DPI_TARGET.lock().unwrap();
  let driver = driver.as_mut().unwrap();
  let issue = driver.issue_instruction();
  *(issue_dst as *mut IssueData) = issue;
}

#[no_mangle]
unsafe extern "C" fn retire_vector_instruction(retire_src: *const SvBitVecVal) {
  let mut driver = DPI_TARGET.lock().unwrap();
  let driver = driver.as_mut().unwrap();
  let retire = &*(retire_src as *const Retire);
  driver.retire_instruction(retire)
}

#[no_mangle]
unsafe extern "C" fn retire_vector_mem(dummy: *const SvBitVecVal) {
  let mut driver = DPI_TARGET.lock().unwrap();
  let driver = driver.as_mut().unwrap();
  driver.retire_memory();
}

//--------------------------------
// import functions and wrappers
//--------------------------------

#[cfg(feature = "trace")]
mod dpi_export {
  use std::ffi::c_char;
  extern "C" {
    /// `export "DPI-C" function dump_wave(input string file)`
    pub fn dump_wave(path: *const c_char);
  }
}

#[cfg(feature = "trace")]
pub(crate) fn dump_wave(scope: crate::svdpi::SvScope, path: &str) {
  use crate::svdpi;
  use std::ffi::CString;
  let path_cstring = CString::new(path).unwrap();

  svdpi::set_scope(scope);
  unsafe {
    dpi_export::dump_wave(path_cstring.as_ptr());
  }
}
