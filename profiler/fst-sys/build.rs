use std::{env, path::PathBuf};

fn main() {
    // Compile C sources to library.
    cc::Build::new()
        .files(["csrc/fastlz.c", "csrc/fstapi.c", "csrc/lz4.c"])
        .define("FST_WRITER_PARALLEL", None)
        .include("csrc")
        .flag_if_supported("-Wno-unused-but-set-variable")
        .compile("fst");

    // Rebuild if C source changes.
    println!("cargo::rerun-if-changed=csrc");

    // Link with zlib.
    println!("cargo::rustc-link-lib=z");

    // Generate bindings.
    let bindings = bindgen::Builder::default()
        .header("csrc/fstapi.h")
        .allowlist_type(r#"(fst|FST_)\w+"#)
        .allowlist_function(r#"(fst|FST_)\w+"#)
        .allowlist_var(r#"(fst|FST_)\w+"#)
        .generate()
        .expect("failed to generate bindings");

    // Write the bindings to file.
    let out_path = PathBuf::from(env::var("OUT_DIR").unwrap()).join("bindings.rs");
    bindings
        .write_to_file(out_path)
        .expect("failed to write bindings");
}
