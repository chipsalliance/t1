use std::sync::{
  atomic::{AtomicU32, Ordering},
  Arc,
};

use tracing::{info, warn};

use super::RegDevice;

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
pub struct SimCtrl {
  exit_flag: ExitFlagRef,
}

impl SimCtrl {
  pub fn new(exit_flag: ExitFlagRef) -> Self {
    SimCtrl { exit_flag }
  }
}

impl RegDevice for SimCtrl {
  fn reg_read(&mut self, offset: u32) -> u32 {
    let _ = offset;
    unimplemented!()
  }

  fn reg_write(&mut self, reg_offset: u32, value: u32) {
    match reg_offset {
      0 => {
        if value == EXIT_CODE {
          self.exit_flag.mark_finish();
          info!("simctrl: write EXIT_POS with EXIT_CODE, ready to quit");
        } else {
          warn!("simctrl: write EXIT_POS with value 0x{value:08x}, ignored");
        }
      }
      _ => panic!(),
    }
  }
}
