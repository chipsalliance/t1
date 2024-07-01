pub mod c_interface;
pub mod spike_event;

pub fn clip(binary: u64, a: i32, b: i32) -> u32 {
  assert!(a <= b, "a should be less than or equal to b");
  let nbits = b - a + 1;
  let mask = if nbits >= 32 {
    u32::MAX
  } else {
    (1 << nbits) - 1
  };
  (binary as u32 >> a) & mask
}
