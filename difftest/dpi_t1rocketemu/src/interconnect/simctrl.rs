use std::{
  fs::File,
  io::Write as _,
  sync::{
    Arc,
    atomic::{AtomicU32, Ordering},
  },
};

use tracing::{error, info, warn};

use crate::get_t;

use super::{BusError, RegDevice};

#[derive(Default, Debug, Clone)]
pub struct ExitFlagRef(Arc<AtomicU32>);

impl ExitFlagRef {
  pub fn new() -> Self {
    Self::default()
  }

  pub fn is_finish(&self) -> bool {
    self.0.load(Ordering::Acquire) != 0
  }

  pub fn mark_finish(&self) {
    self.0.store(1, Ordering::Release);
  }
}

pub const EXIT_CODE: u32 = 0xdead_beef;

/// Reg map:
/// - 0x0000 : WO, write EXIT_CODE to mark simulation finish
/// - 0x0010 : WO, uart write register
/// - 0x0014 : WO, profile write register
///
/// Event file:
/// all writes to uart/profile write register are recorded blindly
/// to "mmio-event.jsonl"
pub struct SimCtrl {
  exit_flag: ExitFlagRef,
  event_file: File,
}

impl SimCtrl {
  pub fn new(exit_flag: ExitFlagRef) -> Self {
    let event_file = File::create("mmio-event.jsonl").unwrap();
    SimCtrl { exit_flag, event_file }
  }

  fn append_event(&mut self, event: &str, value: u32) {
    let cycle = get_t();
    let buf = format!("{{\"cycle\": {cycle}, \"event\": \"{event}\", \"value\": {value}}}\n");

    self.event_file.write_all(buf.as_bytes()).unwrap();
    self.event_file.flush().unwrap();
  }
}

impl RegDevice for SimCtrl {
  fn reg_read(&mut self, offset: u32) -> Result<u32, BusError> {
    let _ = offset;
    error!("simctrl: does not support mmio read");
    Err(BusError)
  }

  fn reg_write(&mut self, reg_offset: u32, value: u32) -> Result<(), BusError> {
    match reg_offset {
      0 => {
        if value == EXIT_CODE {
          self.exit_flag.mark_finish();
          info!("simctrl: write EXIT_POS with EXIT_CODE, ready to quit");
        } else {
          warn!("simctrl: write EXIT_POS with value 0x{value:08x}, ignored");
        }
      }
      0x10 => {
        self.append_event("uart-write", value);
      }
      0x14 => {
        self.append_event("profile", value);
      }
      _ => {
        error!("simctrl: invalid write addr: base + 0x{reg_offset:02x}");
        return Err(BusError);
      }
    }

    Ok(())
  }
}
