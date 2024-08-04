pub struct RTLConfig {
  pub vlen: u32,
  pub dlen: u32,
}

// TODO: read from json

impl RTLConfig {
  pub fn xlen(&self) -> u32 {
    32 // TODO: configurable
  }

  pub fn vlen_in_bytes(&self) -> u32 {
    self.vlen / 8
  }

  pub fn lane_num(&self) -> u32 {
    self.dlen / self.xlen()
  }
}
