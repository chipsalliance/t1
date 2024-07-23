#![allow(non_snake_case)]
#![allow(unused_variables)]

use clap::Parser;
use std::ffi::{c_char, c_int, c_longlong, CString};
use std::ptr;
use tracing::debug;

use crate::sim::{SimulationArgs, Simulator};

pub type SvScalar = u8;
pub type SvBit = SvScalar;
pub type SvBitVecVal = u32;

// --------------------------
// preparing data structures
// --------------------------

///! Read 2^aw_size from *payload, and split it at dlen/16.
///!
///! Return (strobe in bit, data in byte)
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

fn write_to_pointer(dst: *mut u8, data: &[u8], n: usize) {
  unsafe {
    for i in 0..n {
      ptr::write(dst.add(i), data[i]);
    }
  }
}

unsafe fn fill_axi_read_payload(dst: *mut SvBitVecVal, dlen: u32, data: &[u8]) {
  let data_len = (256 / 8) * dlen as usize;
  assert!(data.len() <= data_len);
  let dst = dst as *mut u8;
  write_to_pointer(dst, data, data.len());
}

//----------------------
// dpi functions
//----------------------

#[no_mangle]
unsafe extern "C" fn axi_write_loadStoreAXI_rs(
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
    "axi_write_loadStoreAXI (channel_id={channel_id}, awid={awid}, awaddr={awaddr:#x}, \
  awlen={awlen}, awsize=2^{awsize}, awburst={awburst}, awlock={awlock}, awcache={awcache}, \
  awprot={awprot}, awqos={awqos}, awregion={awregion})"
  );

  let sim = &mut *(target as *mut Simulator);
  let (strobe, data) = load_from_payload(&payload, 1 << awsize, sim.dlen);
  sim.axi_write(awaddr as u32, &strobe, data);
}

#[no_mangle]
unsafe extern "C" fn axi_read_loadStoreAXI_rs(
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
    "axi_read_loadStoreAXI (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  let sim = &mut *(target as *mut Simulator);
  let response = sim.axi_read(araddr as u32, arsize as u64);
  fill_axi_read_payload(payload, sim.dlen, &response.data);
}

#[no_mangle]
unsafe extern "C" fn axi_read_instructionFetchAXI_rs(
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
    "axi_read_instructionFetchAXI (channel_id={channel_id}, arid={arid}, araddr={araddr:#x}, \
  arlen={arlen}, arsize={arsize}, arburst={arburst}, arlock={arlock}, arcache={arcache}, \
  arprot={arprot}, arqos={arqos}, arregion={arregion})"
  );
  let sim = &mut *(target as *mut Simulator);
  let response = sim.axi_read(araddr as u32, arsize as u64);
  fill_axi_read_payload(payload, sim.dlen, &response.data);
}

#[no_mangle]
unsafe extern "C" fn cosim_init_rs(call_init: *mut SvBit) -> *mut () {
  let args = SimulationArgs::parse();
  *call_init = 1;
  let sim = Box::new(Simulator::new(args));
  Box::into_raw(sim) as *mut ()
}

#[no_mangle]
unsafe extern "C" fn cosim_watchdog_rs(target: *mut (), reason: *mut c_char) {
  // watchdog dpi call would be called before initialization, guard on null target
  if !target.is_null() {
    let sim = &mut *(target as *mut Simulator);
    *reason = sim.watchdog() as c_char
  }
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

// FIXME: currently we are using verilator context_p as simulation time.
// But we should implement read cycle at TestBench top
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
}

#[cfg(feature = "trace")]
pub(crate) fn dump_wave(path: &str) {
  let path_cstring = CString::new(path).unwrap();
  let path_ptr: *const c_char = path_cstring.as_ptr();
  unsafe {
    dump_wave_c(path_ptr);
  }
}
