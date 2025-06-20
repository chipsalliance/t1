use std::env;
use std::path::PathBuf;

fn main() {
    // link libpokedex_sim.a
    println!(
        "cargo::rustc-link-search=native={}",
        env::var("POKEDEX_LIB_DIR").expect("POKEDEX_LIB_DIR should be set")
    );
    println!("cargo:rustc-link-lib=static=pokedex_sim");
    println!("cargo::rerun-if-env-changed=POKEDEX_LIB_DIR");

    // link libASL.a
    println!(
        "cargo::rustc-link-search=native={}",
        env::var("ASL_LIB_DIR").expect("ASL_LIB_DIR should be set")
    );
    println!("cargo:rustc-link-lib=static=ASL");
    println!("cargo::rerun-if-env-changed=ASL_LIB_DIR");

    let pokedex_inc_dir = env::var("POKEDEX_INC_DIR").expect("POKEDEX_INC_DIR should be set");
    let asl_inc_dir = env::var("ASL_INC_DIR").expect("ASL_INC_DIR should be set");
    let bindings = bindgen::Builder::default()
        .header("asl_export.h")
        .clang_arg(format!("-I{pokedex_inc_dir}"))
        .clang_arg(format!("-I{asl_inc_dir}"))
        // exclude FFI_* function, this are functions we need to manually implement
        .blocklist_function("^FFI_.*$")
        .parse_callbacks(Box::new(bindgen::CargoCallbacks::new()))
        .generate()
        .unwrap_or_else(|_| panic!("Unable to generate bindings for file asl_export.h"));

    // Write the bindings to the $OUT_DIR/bindings.rs file.
    let out_path = PathBuf::from(env::var("OUT_DIR").unwrap());
    bindings
        .write_to_file(out_path.join("asl_exports.rs"))
        .expect("Couldn't write bindings!");
    println!("cargo::rerun-if-changed=asl_export.h");
}
