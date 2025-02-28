#![allow(non_snake_case)]
#![allow(unused_variables)]

use dpi_common::DpiTarget;
use std::{
  ffi::{c_char, c_longlong, c_ulonglong, CString},
  os::unix::ffi::OsStrExt,
};
use svdpi::dpi::param::InStr;
use svdpi::SvScope;
use tempfile::TempDir;
use tracing::{debug, error, info};

use crate::drive::{Driver, IncompleteRead, IncompleteWrite, OnlineArgs};

pub type SvBitVecVal = u32;

// --------------------------
// preparing data structures
// --------------------------

static TARGET: DpiTarget<Driver> = DpiTarget::new();

/// Wstrb iterator
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
    // The wstrb are transfered in small endian
    let extracted = unsafe { (self.strb.offset(slot as isize).read() >> bit & 1) != 0 };
    self.current += 1;
    Some(extracted)
  }
}

//----------------------
// dpi functions
//----------------------

#[no_mangle]
unsafe extern "C" fn axi_tick(reset: u8) {
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
  unsafe { ready.write(false as u8) };
  if reset != 0 {
    return;
  }
  // dbg!((awid, awaddr, awlen, awsize, awuser, data_width));
  TARGET.with(move |target| {
    target.update_commit_cycle();
    let w = IncompleteWrite::new(awid, awaddr, awlen, awsize, awuser, data_width);
    let fifo = target.incomplete_writes.entry(channel_id).or_default();
    fifo.push_back(w);
    debug!(
      "[{}] Write initialized: channel_id={} id={} at=0x{:x}",
      crate::get_t(),
      channel_id,
      awid,
      awaddr
    );
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
  unsafe { ready.write(false as u8) };
  if reset != 0 {
    return;
  }
  // dbg!((arid, araddr, arlen, arsize, aruser, data_width));
  TARGET.with(move |target| {
    target.update_commit_cycle();
    let r = IncompleteRead::new(araddr, arlen, arsize, aruser, data_width);
    let fifo = target.incomplete_reads.entry((channel_id, arid)).or_default();
    fifo.push_back(r);
    debug!(
      "[{}] Read initialized: channel_id={} id={} at=0x{:x}",
      crate::get_t(),
      channel_id,
      arid,
      araddr
    );
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
  unsafe { ready.write(false as u8) };
  if reset != 0 {
    return;
  }
  TARGET.with(|target| {
    target.update_commit_cycle();
    // TODO: maybe we don't assert this, to allow same-cycle AW/W (when W is sequenced before AW)
    let channel = target
      .incomplete_writes
      .get_mut(&(channel_id))
      .expect("No inflight write with this ID found!");
    let w = channel.iter_mut().find(|w| !w.ready()).expect("No inflight write with this ID found!");
    let wslice = unsafe { std::slice::from_raw_parts(wdata as *const u8, data_width as usize / 8) };
    let wstrbit = StrbIterator {
      strb: wstrb as *const u8,
      total_width: data_width as usize / 8,
      current: 0,
    };
    w.push(wslice, wstrbit, wlast != 0, data_width);
    if wlast != 0 {
      debug!(
        "[{}] Write fully sequenced: channel_id={} id={}",
        crate::get_t(),
        channel_id,
        w.id()
      );
    }
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
  unsafe { ret.write(false as u8) };
  if reset != 0 {
    return;
  }
  TARGET.with(|target| {
    unsafe {
      ret.write(0);
    }
    let fifo = match target.incomplete_writes.get_mut(&channel_id) {
      Some(f) => f,
      None => return,
    };
    // TODO: find later writes with different IDs
    if fifo.front().as_ref().is_none_or(|w| !w.done()) {
      return;
    }
    let w = fifo.pop_front().unwrap();
    debug!(
      "[{}] Write finalized: channel_id={} id={}",
      crate::get_t(),
      channel_id,
      w.id()
    );
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
  unsafe { ret.write(false as u8) };
  if reset != 0 {
    return;
  }
  TARGET.with(|target| {
    unsafe {
      ret.write(0);
    }
    for ((cid, id), fifo) in target.incomplete_reads.iter_mut() {
      if *cid != channel_id {
        continue;
      }; // TODO: we actually wants two levels of HashMap
      if let Some(r) = fifo.front_mut() {
        if r.has_data() {
          let rdata_buf = std::slice::from_raw_parts_mut(ret.offset(8), data_width as usize / 8);
          let last = r.pop(rdata_buf, data_width);
          debug!(
            "[{}] Read data: channel_id={} id={} content={:?}",
            crate::get_t(),
            channel_id,
            *id,
            rdata_buf,
          );
          unsafe {
            ret.write(1);
            ret.offset(1).write(last as u8);
            (ret.offset(2) as *mut u16).write(*id as u16);
            (ret.offset(4) as *mut u32).write(r.user() as u32);
          }

          if last {
            debug!(
              "[{}] Read finalized: channel_id={} id={}",
              crate::get_t(),
              channel_id,
              *id
            );
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
  dramsim3_cfg: InStr<'_>,
  dramsim3_path: InStr<'_>,
) {
  dpi_common::setup_logger();

  let scope = SvScope::get_current().expect("failed to get scope in t1_cosim_init");
  let embedded_cfg_path: CString;

  let dramsim3_cfg_str = dramsim3_cfg.get();
  let dramsim3_cfg_opt = if dramsim3_cfg_str == c"no" {
    None
  } else {
    Some(dramsim3_cfg_str)
  };

  let temp_dramsim3_path: CString;

  let dramsim3_path_str = dramsim3_path.get();
  let dramsim3_cfg_full = match dramsim3_cfg_opt {
    None => None,
    Some(cfg_path) => {
      let run_path = if dramsim3_path_str == c"" {
        let ds3_path = TempDir::new().expect("Failed to create dramsim3 runtime dir");
        temp_dramsim3_path = CString::new(ds3_path.path().as_os_str().as_bytes())
          .expect("Unexpected NULL byte in path");
        std::mem::forget(ds3_path);
        temp_dramsim3_path.as_ref()
      } else {
        dramsim3_path_str
      };
      Some((cfg_path, run_path))
    }
  };

  match dramsim3_cfg_full {
    None => info!("DRAMsim3 disabled"),
    Some((cfg_path, run_path)) => {
      info!(
        "DRAMsim3 enabled with config: {:?}, result: {:?}",
        cfg_path, run_path
      );
    }
  }

  let args = OnlineArgs {
    elf_file: elf_file.get().to_str().unwrap().into(),
    dlen: dlen as u32,
    vlen: vlen as u32,
    spike_isa: spike_isa.get().to_str().unwrap().into(),
    dramsim3: dramsim3_cfg_full,
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
