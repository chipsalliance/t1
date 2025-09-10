use std::env;
use std::path::PathBuf;

fn add_lib_dir(dir_env: &str, lib: &str) {
    println!(
        "cargo::rustc-link-search=native={}",
        env::var(dir_env).unwrap_or_else(|_| format!("{dir_env} should be set"))
    );
    println!("cargo:rustc-link-lib=static={lib}");
    println!("cargo::rerun-if-env-changed={dir_env}");
}

fn main() {
    // link libpokedex_sim.a
    add_lib_dir("POKEDEX_LIB_DIR", "pokedex_model");
    add_lib_dir("ASL_LIB_DIR", "ASL");
    add_lib_dir("SOFTFLOAT_LIB_DIR", "softfloat");
    add_lib_dir("SOFTFLOAT_EXT_LIB_DIR", "softfloat_ext");

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
