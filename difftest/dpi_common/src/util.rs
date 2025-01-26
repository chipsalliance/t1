//! utility functions

use std::path::Path;

pub struct MetaConfig {
  pub vlen: u32,
  pub dlen: u32,
  pub isa: String,
  pub elf_file: Option<String>,
}

pub fn write_perf_json(flavor: &str, cycle: u64, success: bool, meta: &MetaConfig) {
  // unsuccessful simulation will panic by default.
  // However, it could be suppressed by setting env T1_SUPPRESS_PANIC_IN_FINAL=1
  if !success && !suppress_panic_in_final() {
    panic!("simulation ends unsuccessfully [T={cycle}]");
  }

  // we construct json manually,
  // to avoid pull extra dependencies

  let mut content = String::new();
  content += "{\n";
  content += &format!("    \"flavor\": \"{}\",\n", flavor.escape_default());
  content += &format!("    \"meta_vlen\": {},\n", meta.vlen);
  content += &format!("    \"meta_dlen\": {},\n", meta.dlen);
  content += &format!("    \"meta_isa\": \"{}\",\n", meta.isa.escape_default());
  if let Some(elf_file) = &meta.elf_file {
    // we record it for offline use,
    // relative path is not very useful for offline
    if Path::new(elf_file).is_absolute() {
      content += &format!(
        "    \"meta_elf_file\": \"{}\",\n",
        elf_file.escape_default()
      );
    }
  }
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
