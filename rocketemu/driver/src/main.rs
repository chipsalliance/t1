mod dpi;
mod sim;

fn main() {
  println!("starting verilator");
  dpi::verilator_main();
}
