use cmake::Config;

fn main() {
  #[cfg(feature = "trace")]
  let dst =
    Config::new("verilator_shim").define("VM_TRACE", "1").very_verbose(true).always_configure(true).build();
  #[cfg(not(feature = "trace"))]
  let dst = Config::new("verilator_shim").very_verbose(true).always_configure(true).build();

  println!("cargo::rustc-link-search=native={}/lib", dst.display());

  // link order matters!
  // verilator_main <- VTestBench <-- verilated <- verilator_shim <- stdc++
  // verilated <- libz
  // that's why we must split verilator_main and verilator_shim
  println!("cargo::rustc-link-lib=static=verilator_shim");
  println!("cargo::rustc-link-lib=static=VTestBench");
  println!("cargo::rustc-link-lib=static=verilated");
  println!("cargo::rustc-link-lib=stdc++");
  println!("cargo::rustc-link-lib=z");
  println!("cargo::rerun-if-env-changed=VERILATED_LIB_DIR");
}
