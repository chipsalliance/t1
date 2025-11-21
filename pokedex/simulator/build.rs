use std::env;
use std::path::PathBuf;

fn main() {
    let out_path = PathBuf::from(env::var("OUT_DIR").unwrap());

    // FIXME : generate_cstr makes generated code badly formatted, investigate it later
    let pokedex_interface = bindgen::Builder::default()
        .header("include/pokedex_interface.h")
        .allowlist_item(".*POKEDEX.*")
        .allowlist_item(".*pokedex.*")
        // .generate_cstr(true)
        .parse_callbacks(Box::new(bindgen::CargoCallbacks::new()))
        .generate()
        .expect("unable to generate bindinds for pokdex_vtable.h");

    // Write the bindings to the $OUT_DIR/pokedex_interface.rs file.
    pokedex_interface
        .write_to_file(out_path.join("pokedex_interface.rs"))
        .expect("can not write to pokedex_interface.rs");

    if std::env::var("CARGO_FEATURE_BUNDLED_MODEL_LIB").is_ok() {
        let env_name = "POKEDEX_MODEL_LIB";
        let pokedex_model_lib = std::env::var(env_name).unwrap();
        println!("cargo::rustc-link-lib={pokedex_model_lib}");
        println!("cargo::rerun-if-env-changed={env_name}");
    }
}
