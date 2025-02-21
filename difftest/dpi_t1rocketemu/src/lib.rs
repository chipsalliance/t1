mod dpi;
mod drive;
mod interconnect;

// keep in sync with TestBench.verbatimModule
// the value is measured in simulation time unit
pub const CYCLE_PERIOD: u64 = 20000;

/// Real system tCK in ns
pub fn get_sys_tck() -> f64 {
  0.8 // 1.25 GHz
}

/// get cycle
pub fn get_t() -> u64 {
  svdpi::get_time() / CYCLE_PERIOD
}
