use crate::dpi_bind::verilator_main_wrapped;

mod dpi_bind;

fn main() {
  verilator_main_wrapped();
  println!("Hello, world!");
}
