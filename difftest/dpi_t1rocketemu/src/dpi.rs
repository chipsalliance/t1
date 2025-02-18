#![allow(non_snake_case)]
#![allow(unused_variables)]

use dpi_common::DpiTarget;
use std::ffi::{c_char, c_longlong, c_ulonglong};
use svdpi::dpi::param::InStr;
use svdpi::SvScope;
use tracing::error;

use crate::drive::{Driver, IncompleteRead, IncompleteWrite, OnlineArgs};

pub type SvBitVecVal = u32;

// --------------------------
// preparing data structures
// --------------------------

static TARGET: DpiTarget<Driver> = DpiTarget::new();

struct StrbIterator {
  strb: *const u8,
  total_width: usize,
  current: usize,
}

impl Iterator for StrbIterator {
  type Item = bool;
  fn next(&mut self) -> Option<Self::Item> {
    if self.total_width == self.current {
      return None;
    }
    assert!(self.total_width > self.current);

    let slot = self.current / 8;
    let bit = self.current % 8;
    // TODO: is small endian correct??
    let extracted = unsafe { self.strb.offset(slot as isize).read() >> bit != 0 };
    self.current += 1;
    Some(extracted)
  }
}

//----------------------
// dpi functions
//----------------------

#[no_mangle]
unsafe extern "C" fn axi_tick(
  reset: u8,
) {
  TARGET.with(|driver| {
    driver.tick();
  })
}

#[no_mangle]
unsafe extern "C" fn axi_push_AW(
  reset: u8,
  channel_id: c_ulonglong,
  data_width: u64,
  awid: c_ulonglong,
  awaddr: c_ulonglong,
  awsize: c_ulonglong,
  awlen: c_ulonglong,
  awuser: c_ulonglong,

  ready: *mut u8,
) {
  if reset != 0 { return; }
  TARGET.with(move |target| {
    target.update_commit_cycle();
    let w = IncompleteWrite::new(awid, awaddr, awlen, awsize, awuser, data_width);
    let fifo = target.incomplete_writes.entry(channel_id).or_default();
    fifo.push_back(w);
  });
  unsafe { ready.write(true as u8) };
}

#[no_mangle]
unsafe extern "C" fn axi_push_AR(
  reset: u8,
  channel_id: c_ulonglong,
  data_width: u64,
  arid: c_ulonglong,
  araddr: c_ulonglong,
  arsize: c_ulonglong,
  arlen: c_ulonglong,
  aruser: c_ulonglong,

  ready: *mut u8,
) {
  if reset != 0 { return; }
  TARGET.with(move |target| {
    target.update_commit_cycle();
    let r = IncompleteRead::new(araddr, arlen, arsize, aruser, data_width);
    let fifo = target.incomplete_reads.entry((channel_id, arid)).or_default();
    fifo.push_back(r);
  });
  unsafe { ready.write(true as u8) };
}

#[no_mangle]
unsafe extern "C" fn axi_push_W(
  reset: u8,
  channel_id: c_ulonglong,
  data_width: u64,
  wdata: *const SvBitVecVal,
  wstrb: *const SvBitVecVal,
  wlast: c_char,

  ready: *mut u8,
) {
  if reset != 0 { return; }
  TARGET.with(|target| {
    target.update_commit_cycle();
    // TODO: maybe we don't assert this, to allow same-cycle AW/W (when W is sequenced before AW)
    let channel = target.incomplete_writes.get_mut(&(channel_id)).expect("No inflight write with this ID found!");
    let w = channel.iter_mut().find(|w| !w.ready()).expect("No inflight write with this ID found!");
    let wslice = unsafe { std::slice::from_raw_parts(wdata as *const u8, data_width as usize / 8) };
    let wstrbit = StrbIterator {
      strb: wstrb as *const u8,
      total_width: data_width as usize / 8,
      current: 0,
    };
    w.push(wslice, wstrbit, wlast != 0, data_width);
    unsafe { ready.write(true as u8) };
  })
}

#[no_mangle]
unsafe extern "C" fn axi_pop_B(
  reset: u8,
  channel_id: c_ulonglong,
  data_width: u64,
  
  // Packed result buffer:
  // ret[0]: valid
  // ret[2..3]: BID
  // ret[4..8]: BUSER
  ret: *mut u8,
) {
  if reset != 0 { return; }
  TARGET.with(|target| {
    unsafe { ret.write(0); }
    target.update_commit_cycle();
    let fifo = target.incomplete_writes.get_mut(&channel_id).expect("No inflight write with this ID found!");
    // TODO: find later writes with different IDs
    if fifo.front().as_ref().is_none_or(|w| !w.done()) { return; }
    let w = fifo.pop_front().unwrap();
    unsafe {
      ret.write(1);
      (ret.offset(2) as *mut u16).write(w.id() as u16);
      (ret.offset(4) as *mut u32).write(w.user() as u32);
    }
    return;
  })
}

#[no_mangle]
unsafe extern "C" fn axi_pop_R(
  reset: u8,
  channel_id: c_ulonglong,
  data_width: u64,
  
  // Packed result buffer:
  // ret[0]: valid
  // ret[1]: rlast
  // ret[2..3]: rid
  // ret[4..8]: ruser (in 32 bit)
  // ret[8..]: rdata
  ret: *mut u8,
) {
  if reset != 0 { return; }
  TARGET.with(|target| {
    unsafe { ret.write(0); }
    target.update_commit_cycle();
    for ((cid, id), fifo) in target.incomplete_reads.iter_mut() {
      if *cid != channel_id { continue }; // TODO: we actually wants two levels of HashMap
      if let Some(r) = fifo.front_mut() {
        if r.has_data() {
          let rdata_buf = std::slice::from_raw_parts_mut(ret.offset(8), data_width as usize / 8);
          let last = r.pop(rdata_buf, data_width);
          unsafe {
            ret.write(1);
            ret.offset(1).write(last as u8);
            (ret.offset(2) as *mut u16).write(*id as u16);
            (ret.offset(4) as *mut u32).write(r.user() as u32);
          }

          if last {
            fifo.pop_front();
          }
          return;
        }
      }
    }
  })
}

#[no_mangle]
unsafe extern "C" fn t1_cosim_init(
  elf_file: InStr<'_>,
  dlen: i32,
  vlen: i32,
  spike_isa: InStr<'_>,
) {
  dpi_common::setup_logger();

  let scope = SvScope::get_current().expect("failed to get scope in t1_cosim_init");

  use std::io::Write;
  let ds3_cfg = include_bytes!("dramsim3-config.ini");
  let mut ds3_cfg_file = tempfile::NamedTempFile::new().expect("Unable to create DRAMsim3 configuration temp file");
  ds3_cfg_file.write(ds3_cfg).expect("Unable to write DRAMsim3 configuration temp file");
  let (_, ds3_cfg_path) = ds3_cfg_file.keep().expect("Unable to persist DRAMsim3 configuration temp file");

  let args = OnlineArgs {
    elf_file: elf_file.get().to_str().unwrap().into(),
    dlen: dlen as u32,
    vlen: vlen as u32,
    spike_isa: spike_isa.get().to_str().unwrap().into(),
    dramsim3_cfg : Some(ds3_cfg_path),
  };

  TARGET.init(|| Driver::new(scope, &args));
}

#[no_mangle]
unsafe extern "C" fn t1_cosim_final() {
  TARGET.with_optional(|driver| {
    if let Some(driver) = driver {
      let success = driver.exit_flag.is_finish();
      dpi_common::util::write_perf_json("t1rocketemu", crate::get_t(), success, &driver.meta);
    } else {
      error!("'sim_result.json' generation skipped due to panic in DPI side");
    }
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
  TARGET.with(|driver| {
    *resetvector = driver.e_entry as c_longlong;
  });
}

//--------------------------------
// import functions and wrappers
//--------------------------------
