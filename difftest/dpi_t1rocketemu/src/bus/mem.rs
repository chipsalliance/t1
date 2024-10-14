use super::ShadowDevice;

pub(super) struct MemDevice<const SIZE: usize> {
  mem: Box<[u8; SIZE]>,
}

impl<const SIZE: usize> ShadowDevice for MemDevice<SIZE> {
  fn new() -> Box<dyn ShadowDevice>
  where
    Self: Sized,
  {
    Box::new(Self { mem: vec![0u8; SIZE].try_into().unwrap() })
  }

  fn read_mem(&self, addr: usize, size: usize) -> &[u8] {
    let start = addr;
    let end = addr + size;
    &self.mem[start..end]
  }

  fn write_mem(&mut self, addr: usize, data: u8) {
    self.mem[addr] = data;
  }

  fn write_mem_chunk(&mut self, addr: usize, size: usize, strobe: Option<&[bool]>, data: &[u8]) {
    // NOTE: addr & size alignment check already done in ShadowBus, and ELF load can be unaligned anyway.

    if let Some(masks) = strobe {
      masks.iter().enumerate().for_each(|(i, mask)| {
        if *mask {
          self.mem[addr + i] = data[i];
        }
      })
    } else {
      let start = addr;
      let end = addr + size;
      self.mem[start..end].copy_from_slice(data);
    }
  }
}
