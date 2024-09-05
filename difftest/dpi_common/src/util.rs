//! utility functions

pub fn write_perf_json(cycle: u64, success: bool) {
  // unsuccessful simulation will panic by default.
  // However, it could be suppressed by setting env T1_SUPPRESS_PANIC_IN_FINAL=1
  if !success && !suppress_panic_in_final() {
    panic!("simulation ends unsuccessfully [T={cycle}]");
  }

  let mut content = String::new();
  content += "{\n";
  content += &format!("    \"total_cycles\": {cycle},\n");
  content += &format!("    \"success\": {success}\n");
  content += "}\n";

  match std::fs::write("perf.json", &content) {
    Ok(()) => {}
    Err(e) => {
      tracing::error!("failed to write 'perf.json': {e}");
    }
  }
}

fn suppress_panic_in_final() -> bool {
  std::env::var("T1_SUPPRESS_PANIC_IN_FINAL").as_deref() == Ok("1")
}
