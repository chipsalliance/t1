#![allow(non_snake_case)]
#![allow(unused_variables)]

use dpi_common::DpiTarget;
use std::{ffi::c_longlong, sync::OnceLock};
use svdpi::dpi::param::InStr;
use svdpi::SvScope;
use tracing::debug;

use crate::drive::{Driver, OnlineArgs};

pub type SvBitVecVal = u32;

// --------------------------
// preparing data structures
// --------------------------

static TARGET: DpiTarget<Driver> = DpiTarget::new();

static DESIGN_PARAM: OnceLock<DesignParam> = OnceLock::new();

pub struct DesignParam {
  // time unit of Verbatim Module
  pub cg_timescale: i32,
  // simulation time unit, -12 -> 1ps, -9 -> 1ns, etc
  pub sim_timescale: i32,
  // measured in sim time unit
  pub clock_period: u64,

  // scope of VerbatimModule (ClockGen)
  pub cg_scope: SvScope,

  pub dlen: u32,
  pub vlen: u32,
  pub spike_isa: String,
}

impl DesignParam {
  #[track_caller]
  pub fn get() -> &'static DesignParam {
    // it should be init by t1_cosim_preinit
    DESIGN_PARAM.get().expect("DESIGN_PARAM is not set")
  }

  pub fn get_cycle(&self) -> u64 {
    svdpi::get_time() / self.clock_period
  }
}

pub fn get_t() -> u64 {
  DesignParam::get().get_cycle()
}

unsafe fn write_to_pointer(dst: *mut u8, data: &[u8]) {
  let dst = std::slice::from_raw_parts_mut(dst, data.len());
  dst.copy_from_slice(data);
}

unsafe fn fill_axi_read_payload(dst: *mut SvBitVecVal, dlen: u32, payload: &[u8]) {
  assert!(payload.len() * 8 <= dlen as usize);
  write_to_pointer(dst as *mut u8, payload);
}

// Return (strobe in bit, data in byte)
// data_width: AXI width (count in bits)
// size: AXI transaction bytes ( data_width * (1 + MAX_AWLEN) / 8 )
unsafe fn load_from_payload(
  payload: &*const SvBitVecVal,
  data_width: u32,
  size: u32,
) -> (Vec<bool>, &[u8]) {
  let src = *payload as *mut u8;
  let data_width_in_byte = std::cmp::max(size, 4) as usize;
  let strb_width_per_byte = (data_width / 8).min(8) as usize;
  let strb_width_in_byte = (size as usize).div_ceil(strb_width_per_byte);

  let payload_size_in_byte = strb_width_in_byte + data_width_in_byte; // data width in byte
  let byte_vec = std::slice::from_raw_parts(src, payload_size_in_byte);
  let strobe = &byte_vec[..strb_width_in_byte];
  let data = &byte_vec[strb_width_in_byte..];

  let masks: Vec<bool> = strobe
    .into_iter()
    .flat_map(|strb| {
      let mask: Vec<bool> = (0..strb_width_per_byte).map(|i| (strb & (1 << i)) != 0).collect();
      mask
    })
    .collect();

  assert_eq!(masks.len(), data.len());

  (masks, data)
}

//----------------------
// dpi functions
//----------------------

/// evaluate after AW and W is finished at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_write_highBandwidthAXI(
  channel_id: c_longlong,
  data_width: i64,
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
    assert_eq!(data_width as u32, driver.dlen);
    assert_eq!(awlen, 0);

    let (strobe, data) = load_from_payload(&payload, driver.dlen, driver.dlen / 8);
    driver.axi_write(awaddr as u32, awsize as u32, driver.dlen, &strobe, data);

    driver.update_commit_cycle();
  });
}

/// evaluate at AR fire at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_read_highBandwidthAXI(
  channel_id: c_longlong,
  data_width: i64,
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
    let dlen = driver.dlen;
    assert_eq!(data_width as u32, dlen);
    assert_eq!(arlen, 0);

    let response = driver.axi_read(araddr as u32, arsize as u32, 0, dlen);
    fill_axi_read_payload(payload, dlen, &response);

    driver.update_commit_cycle();
  });
}

/// evaluate after AW and W is finished at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_write_highOutstandingAXI(
  channel_id: c_longlong,
  data_width: i64,
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
    assert_eq!(data_width, 32);
    assert_eq!(awlen, 0);

    let (strobe, data) = load_from_payload(&payload, 32, 32 / 8);
    driver.axi_write(awaddr as u32, awsize as u32, 32, &strobe, data);

    driver.update_commit_cycle();
  });
}

/// evaluate at AR fire at corresponding channel_id.
#[no_mangle]
unsafe extern "C" fn axi_read_highOutstandingAXI(
  channel_id: c_longlong,
  data_width: i64,
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
    assert_eq!(data_width, 32);
    assert_eq!(arlen, 0);

    let response = driver.axi_read(araddr as u32, arsize as u32, 0, 32);
    fill_axi_read_payload(payload, 32, &response);

    driver.update_commit_cycle();
  });
}

#[no_mangle]
unsafe extern "C" fn axi_write_loadStoreAXI(
  channel_id: c_longlong,
  data_width: i64,
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
    assert_eq!(data_width, 32);
    assert_eq!(awlen, 0);

    let (strobe, data) = load_from_payload(&payload, 32, 8 * 32 / 8);
    let strobe = &strobe[..4];
    let data = &data[..4];
    driver.axi_write(awaddr as u32, awsize as u32, 32, strobe, data);

    driver.update_commit_cycle();
  });
}

#[no_mangle]
unsafe extern "C" fn axi_read_loadStoreAXI(
  channel_id: c_longlong,
  data_width: i64,
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
  // chisel use sync reset, registers are not reset at time=0
  // to avoid DPI trace (especially in verilator), we filter it here
  if svdpi::get_time() == 0 {
    debug!("axi_read_loadStoreAXI (ignored at time zero)");
    // TODO: better to fill zero to payload, but maintain the correct length for payload is too messy
    return;
  }

  debug!(
    "axi_read_loadStoreAXI (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  TARGET.with(|driver| {
    assert_eq!(data_width, 32);
    assert_eq!(arlen, 0);

    let response = driver.axi_read(araddr as u32, arsize as u32, 0, 32);
    fill_axi_read_payload(payload, 8 * 32, &response);

    driver.update_commit_cycle();
  });
}

#[no_mangle]
unsafe extern "C" fn axi_read_instructionFetchAXI(
  channel_id: c_longlong,
  data_width: i64,
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
  // chisel use sync reset, registers are not reset at time=0
  // to avoid DPI trace (especially in verilator), we filter it here
  if svdpi::get_time() == 0 {
    debug!("axi_read_instructionFetchAXI (ignored at time zero)");
    // TODO: better to fill zero to payload, but maintain the correct length for payload is too messy
    return;
  }

  debug!(
    "axi_read_instructionFetchAXI (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  TARGET.with(|driver| {
    assert_eq!(data_width, 32);
    assert_eq!(arlen, 7);

    assert_eq!(8 * (1 << arsize), data_width);

    let response = driver.axi_read(araddr as u32, arsize as u32, arlen as u32, 32);
    fill_axi_read_payload(payload, 256, &response);
  });
}

#[no_mangle]
unsafe extern "C" fn t1_cosim_preinit(dlen: i32, vlen: i32, spike_isa: InStr<'_>) {
  dpi_common::setup_logger();

  let cg_scope = SvScope::get_current().expect("failed to get SvScope");

  // TODO: when sv2023 is pervasive, we could use svTimeUnit/Precision
  // 1ns/1ps, keep in sync with TestBench.verbatimModule
  let cg_timescale = -9;
  let sim_timescale = -12;

  // keep in sync with TestBench.verbatimModule
  const CLOCK_PERIOD_IN_VERBATIM_TIME_UNIT: u64 = 20;

  assert!(cg_timescale >= sim_timescale);

  let clock_period =
    10u64.pow((cg_timescale - sim_timescale) as u32) * CLOCK_PERIOD_IN_VERBATIM_TIME_UNIT;

  DESIGN_PARAM
    .set(DesignParam {
      cg_timescale,
      sim_timescale,
      clock_period,
      cg_scope,
      dlen: dlen as u32,
      vlen: vlen as u32,
      spike_isa: spike_isa.get().to_str().unwrap().into(),
    })
    .unwrap_or_else(|_| panic!("DESIGN_PARAM is already set"));
}

#[no_mangle]
unsafe extern "C" fn t1_cosim_init(elf_file: InStr<'_>) {
  let args = OnlineArgs {
    elf_file: elf_file.get().to_str().unwrap().into(),
    dlen: DesignParam::get().dlen,
  };

  TARGET.init(|| Driver::new(&args));
}

#[no_mangle]
unsafe extern "C" fn t1_cosim_final() {
  TARGET.with(|driver| {
    let success = driver.exit_flag.is_finish();
    dpi_common::util::write_perf_json(crate::get_t(), success);
  });
}

#[no_mangle]
unsafe extern "C" fn t1_cosim_set_timeout(timeout: u64) {
  TARGET.with(|driver| driver.set_timeout(timeout));
}

/// evaluate at every cycle
/// return value:
///   0   : continue
///   255 : quit successfully
///   otherwise : error
#[no_mangle]
unsafe extern "C" fn t1_cosim_watchdog() -> u8 {
  TARGET.with(|driver| driver.watchdog())
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
