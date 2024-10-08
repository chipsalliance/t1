#![allow(non_snake_case)]
#![allow(unused_variables)]

use dpi_common::dump::DumpControl;
use dpi_common::plusarg::PlusArgMatcher;
use dpi_common::DpiTarget;
use std::ffi::{c_char, c_longlong};
use svdpi::SvScope;
use tracing::debug;

use crate::drive::Driver;
use crate::OnlineArgs;

pub type SvBitVecVal = u32;

// --------------------------
// preparing data structures
// --------------------------

static TARGET: DpiTarget<Driver> = DpiTarget::new();

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
unsafe fn load_from_payload(
  payload: &*const SvBitVecVal,
  data_width: usize,
  size: usize,
) -> (Vec<bool>, &[u8]) {
  let src = *payload as *mut u8;
  let data_width_in_byte = std::cmp::max(size, 4);
  let strb_width_per_byte = if data_width < 64 { 4 } else { 8 };
  let strb_width_in_byte = size.div_ceil(strb_width_per_byte);

  let payload_size_in_byte = strb_width_in_byte + data_width_in_byte; // data width in byte
  let byte_vec = std::slice::from_raw_parts(src, payload_size_in_byte);
  let strobe = &byte_vec[0..strb_width_in_byte];
  let data = &byte_vec[strb_width_in_byte..];

  let masks: Vec<bool> = strobe
    .into_iter()
    .flat_map(|strb| {
      let mask: Vec<bool> = (0..strb_width_per_byte).map(|i| (strb & (1 << i)) != 0).collect();
      mask
    })
    .collect();
  assert_eq!(
    masks.len(),
    data.len(),
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

//----------------------
// dpi functions
//----------------------

/// evaluate after AW and W is finished at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_write_highBandwidthAXI(
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
  TARGET.with(|driver| {
    let (strobe, data) = load_from_payload(&payload, driver.dlen as usize, (1 << awsize) as usize);
    driver.axi_write_high_bandwidth(awaddr as u32, awsize as u64, &strobe, data);
  });
}

/// evaluate at AR fire at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_read_highBandwidthAXI(
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
  TARGET.with(|driver| {
    let response = driver.axi_read_high_bandwidth(araddr as u32, arsize as u64);
    fill_axi_read_payload(payload, driver.dlen, &response);
  });
}

/// evaluate after AW and W is finished at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_write_highOutstandingAXI(
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
  // struct packed {bit [255:0][31:0] data; bit [255:0][3:0] strb; } payload
  payload: *const SvBitVecVal,
) {
  debug!(
    "axi_write_high_outstanding (channel_id={channel_id}, awid={awid}, awaddr={awaddr:#x}, \
  awlen={awlen}, awsize={awsize}, awburst={awburst}, awlock={awlock}, awcache={awcache}, \
  awprot={awprot}, awqos={awqos}, awregion={awregion})"
  );
  TARGET.with(|driver| {
    let (strobe, data) = load_from_payload(&payload, 32, (1 << awsize) as usize);
    driver.axi_write_high_outstanding(awaddr as u32, awsize as u64, &strobe, data);
  });
}

/// evaluate at AR fire at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_read_highOutstandingAXI(
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
    "axi_read_high_outstanding (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  TARGET.with(|driver| {
    let response = driver.axi_read_high_outstanding(araddr as u32, arsize as u64);
    fill_axi_read_payload(payload, driver.dlen, &response);
  });
}

#[no_mangle]
unsafe extern "C" fn axi_write_loadStoreAXI(
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
    "axi_write_loadStore (channel_id={channel_id}, awid={awid}, awaddr={awaddr:#x}, \
  awlen={awlen}, awsize={awsize}, awburst={awburst}, awlock={awlock}, awcache={awcache}, \
  awprot={awprot}, awqos={awqos}, awregion={awregion})"
  );
  TARGET.with(|driver| {
    let data_width = if awsize <= 2 { 32 } else { 8 * (1 << awsize) } as usize;
    let (strobe, data) = load_from_payload(&payload, data_width, (driver.dlen / 8) as usize);
    driver.axi_write_load_store(awaddr as u32, awsize as u64, &strobe, data);
  });
}

#[no_mangle]
unsafe extern "C" fn axi_read_loadStoreAXI(
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
    "axi_read_loadStoreAXI (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  TARGET.with(|driver| {
    let response = driver.axi_read_load_store(araddr as u32, arsize as u64);
    fill_axi_read_payload(payload, driver.dlen, &response);
  });
}

#[no_mangle]
unsafe extern "C" fn axi_read_instructionFetchAXI(
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
    "axi_read_instructionFetchAXI (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  TARGET.with(|driver| {
    let response = driver.axi_read_instruction_fetch(araddr as u32, arsize as u64);
    fill_axi_read_payload(payload, driver.dlen, &response);
  });
}

#[no_mangle]
unsafe extern "C" fn t1rocket_cosim_init() {
  let plusargs = PlusArgMatcher::from_args();
  let args = OnlineArgs::from_plusargs(&plusargs);

  dpi_common::setup_logger();

  let scope = SvScope::get_current().expect("failed to get scope in t1rocket_cosim_init");
  let dump_control = DumpControl::from_plusargs(scope, &plusargs);

  TARGET.init(|| Driver::new(scope, dump_control, &args));
}

#[no_mangle]
unsafe extern "C" fn t1rocket_cosim_final() {
  TARGET.with(|driver| {
    dpi_common::util::write_perf_json(crate::get_t(), driver.success);
  });
}

/// evaluate at every 1024 cycles, return reason = 0 to continue simulation,
/// other value is used as error code.
#[no_mangle]
unsafe extern "C" fn cosim_watchdog(reason: *mut c_char) {
  // watchdog dpi call would be called before initialization, guard on null target
  TARGET.with_optional(|driver| {
    if let Some(driver) = driver {
      *reason = driver.watchdog() as c_char;
    }
  });
}

/// evaluate at every cycle, return quit_flag = false to continue simulation,
#[no_mangle]
unsafe extern "C" fn cosim_quit(quit_flag: *mut bool) {
  // watchdog dpi call would be called before initialization, guard on null target
  TARGET.with_optional(|driver| {
    if let Some(driver) = driver {
      *quit_flag = driver.quit as bool;
    }
  });
}

#[no_mangle]
unsafe extern "C" fn get_resetvector(resetvector: *mut c_longlong) {
  TARGET.with_optional(|driver| {
    if let Some(driver) = driver {
      *resetvector = driver.e_entry as c_longlong;
    }
  });
}

//--------------------------------
// import functions and wrappers
//--------------------------------
