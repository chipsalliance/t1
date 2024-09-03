use svdpi::SvScope;
use tracing::error;

use crate::plusarg::PlusArgMatcher;

pub struct DumpEndError;

#[cfg(feature = "trace")]
pub type DumpControl = RealDumpControl;
#[cfg(not(feature = "trace"))]
pub type DumpControl = EmptyDumpControl;

#[cfg(feature = "trace")]
mod dpi_export {
  use std::ffi::c_char;
  extern "C" {
    /// `export "DPI-C" function dump_wave(input string file)`
    pub fn dump_wave(path: *const c_char);
  }
}

#[cfg(feature = "trace")]
fn dump_wave(scope: svdpi::SvScope, path: &str) {
  use std::ffi::CString;
  let path_cstring = CString::new(path).unwrap();

  svdpi::set_scope(scope);
  unsafe {
    dpi_export::dump_wave(path_cstring.as_ptr());
  }
}

#[cfg(feature = "trace")]
pub struct RealDumpControl {
  svscope: svdpi::SvScope,
  wave_path: String,
  dump_start: u64,
  dump_end: u64,

  started: bool,
}

#[cfg(feature = "trace")]
impl RealDumpControl {
  fn new(svscope: SvScope, wave_path: &str, dump_range: &str) -> Self {
    let (dump_start, dump_end) = parse_range(dump_range);
    Self {
      svscope,
      wave_path: wave_path.to_owned(),
      dump_start,
      dump_end,

      started: false,
    }
  }

  pub fn from_plusargs(svscope: SvScope, matcher: &PlusArgMatcher) -> Self {
    let wave_path = matcher.match_("t1_wave_path");
    let dump_range = matcher.try_match("t1_dump_range").unwrap_or("");
    Self::new(svscope, wave_path, dump_range)
  }

  pub fn start(&mut self) {
    if !self.started {
      dump_wave(self.svscope, &self.wave_path);
      self.started = true;
    }
  }

  pub fn trigger_watchdog(&mut self, tick: u64) -> Result<(), DumpEndError> {
    if self.dump_end != 0 && tick > self.dump_end {
      return Err(DumpEndError);
    }

    if tick >= self.dump_start {
      self.start();
    }

    Ok(())
  }
}

pub struct EmptyDumpControl {}
impl EmptyDumpControl {
  pub fn from_plusargs(svscope: SvScope, matcher: &PlusArgMatcher) -> Self {
    // do nothing
    let _ = svscope;
    let _ = matcher;
    Self {}
  }
  pub fn start(&mut self) {
    // do nothing
  }
  pub fn trigger_watchdog(&mut self, tick: u64) -> Result<(), DumpEndError> {
    // do nothing
    let _ = tick;
    Ok(())
  }
}

fn parse_range(input: &str) -> (u64, u64) {
  if input.is_empty() {
    return (0, 0);
  }

  let parts: Vec<&str> = input.split(",").collect();

  if parts.len() != 1 && parts.len() != 2 {
    error!("invalid dump wave range: `{input}` was given");
    return (0, 0);
  }

  const INVALID_NUMBER: &'static str = "invalid number";

  if parts.len() == 1 {
    return (parts[0].parse().expect(INVALID_NUMBER), 0);
  }

  if parts[0].is_empty() {
    return (0, parts[1].parse().expect(INVALID_NUMBER));
  }

  let start = parts[0].parse().expect(INVALID_NUMBER);
  let end = parts[1].parse().expect(INVALID_NUMBER);
  if start > end {
    panic!("dump start is larger than end: `{input}`");
  }

  (start, end)
}
