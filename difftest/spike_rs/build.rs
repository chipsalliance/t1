use std::env;

fn main() {
  println!(
    "cargo::rustc-link-search=native={}",
    env::var("SPIKE_LIB_DIR").expect("SPIKE_LIB_DIR should be set")
  );
  println!("cargo::rustc-link-lib=static=riscv");
  println!("cargo::rustc-link-lib=static=softfloat");
  println!("cargo::rustc-link-lib=static=disasm");
  println!("cargo::rustc-link-lib=static=fesvr");
  println!("cargo::rustc-link-lib=static=fdt");

  println!(
    "cargo::rustc-link-search=native={}",
    env::var("SPIKE_INTERFACES_LIB_DIR").expect("SPIKE_INTERFACES_LIB_DIR should be set")
  );
  println!("cargo::rustc-link-lib=static=spike_interfaces");

  println!("cargo::rerun-if-env-changed=SPIKE_LIB_DIR");
  println!("cargo::rerun-if-env-changed=SPIKE_INTERFACES_LIB_DIR");

  println!("cargo::rustc-link-lib=stdc++");
}
