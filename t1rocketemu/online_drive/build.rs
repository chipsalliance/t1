use cmake::Config;

fn main() {
  #[cfg(feature = "trace")]
  let dst =
    Config::new("verilator_shim").define("VM_TRACE", "1").very_verbose(true).always_configure(true).build();
  #[cfg(not(feature = "trace"))]
  let dst = Config::new("verilator_shim").very_verbose(true).always_configure(true).build();

  println!("cargo::rustc-link-search=native={}/lib", dst.display());

  // link order matters! so we use +whole-archive here
  // verilator_main <- VTestBench <-- verilated <- verilator_shim <- stdc++
  // verilated <- libz
  println!("cargo::rustc-link-lib=static:+whole-archive=verilator_shim");
  println!("cargo::rustc-link-lib=static:+whole-archive=VTestBench");
  println!("cargo::rustc-link-lib=static:+whole-archive=verilated");
  println!("cargo::rustc-link-lib=stdc++");
  println!("cargo::rustc-link-lib=z");
  println!("cargo::rerun-if-env-changed=VERILATED_LIB_DIR");
}
