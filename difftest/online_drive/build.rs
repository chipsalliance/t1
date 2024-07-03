use cmake::Config;

fn main() {
    let dst = Config::new("dpi_c")
        .very_verbose(true)
        .build();

    println!("cargo:rustc-link-search=native={}/lib", dst.display());

    // link order matters!
    // verilator_main <- VTestBench <-- verilated <- dpi_c <- stdc++
    // that's why we must split verilator_main and dpi_c
    println!("cargo:rustc-link-lib=static=verilator_main");
    println!("cargo:rustc-link-lib=static=VTestBench");
    println!("cargo:rustc-link-lib=static=verilated");
    println!("cargo:rustc-link-lib=static=dpi_c");
    println!("cargo:rustc-link-lib=static=stdc++");
}
