use std::env;
use std::path::PathBuf;

fn main() {
    let sail_install_path = env::var("SAIL_INSTALL_PATH").expect("$SAIL_INSTALL_PATH not set");

    let bindings = bindgen::Builder::default()
        .header("sail_include/wrapper.h")
        .clang_arg(format!("-I{sail_install_path}/share/sail/lib"))
        .parse_callbacks(Box::new(bindgen::CargoCallbacks::new()))
        .generate()
        .expect("Unable to generate bindings");

    // Write the bindings to the $OUT_DIR/bindings.rs file.
    let out_path = PathBuf::from(env::var("OUT_DIR").unwrap());
    bindings
        .write_to_file(out_path.join("sail_h.rs"))
        .expect("Couldn't write bindings!");
}
