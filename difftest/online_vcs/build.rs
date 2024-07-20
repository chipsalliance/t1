use std::path::Path;
use std::env;

fn main() {
  let vcs_lib_dir = env::var("VCS_LIB_DIR").unwrap();
  println!("cargo:rustc-link-search=native={}", Path::new(&vcs_lib_dir).display());
  let vcs_compiled_lib_dir = env::var("VCS_COMPILED_LIB_DIR").unwrap();
  println!("cargo:rustc-env=LD_LIBRARY_PATH={}", Path::new(&vcs_lib_dir).display());
  println!("cargo:rustc-link-search=native={}", Path::new(&vcs_compiled_lib_dir).display());
  println!("cargo::rustc-link-lib=TestBench");
  println!("cargo::rustc-link-lib=vcsnew64");
  println!("cargo::rustc-link-lib=vcs_tls");
  println!("cargo::rustc-link-lib=vfs");
}
