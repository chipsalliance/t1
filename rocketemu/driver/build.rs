fn main() {
  const SEARCH_DIRS: [&str; 2] = ["ROCKET_DPI_DIR", "TESTBENCH_LIB_DIR"];
  SEARCH_DIRS.iter().for_each(|env| {
    let dir =
      std::env::var(env).unwrap_or_else(|_| panic!("ERROR: {} environment variable not set", &env));
    println!("cargo:rustc-link-search=native={}/lib", &dir);
    println!("cargo:rerun-if-env-changed={}", env);
  });

  // link order matters!
  // verilator_main <- VTestBench <-- verilated <- dpi_c <- stdc++
  // verilated <- libz
  // that's why we must split verilator_main and dpi_c
  println!("cargo:rustc-link-lib=static=dpi_pre_link");
  println!("cargo:rustc-link-lib=static=VTestBench");
  println!("cargo:rustc-link-lib=static=verilated");
  println!("cargo:rustc-link-lib=static=dpi");
  println!("cargo:rustc-link-lib=static=stdc++");
  println!("cargo:rustc-link-lib=dylib=z");
}
