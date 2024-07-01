use cmake::Config;

fn main() {
    let dst = Config::new("dpi_c")
        .very_verbose(true)
        .build();

    println!("cargo:rustc-link-search=native={}/lib", dst.display());
    println!("cargo:rustc-link-lib=static=dpi_c");
    println!("cargo:rustc-link-lib=static=VTestBench");
    println!("cargo:rustc-link-lib=static=verilated");
    println!("cargo:rustc-link-lib=static=stdc++");
}
