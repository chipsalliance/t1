use std::any::Any;

use framebuffer::FrameBuffer;

pub mod framebuffer;

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub struct AddrInfo {
  pub offset: u32,
  pub len: u32,
}

impl AddrInfo {
  pub fn as_range(self) -> std::ops::Range<usize> {
    self.offset as usize..(self.offset + self.len) as usize
  }
}

// Caller is reponsible to ensure the following conditions hold:
//   addr.len > 0
//   addr.len == data.len()
//   addr.len == mask.len() (if mask present)
// However, since the functions are safe,
// even if contracts violate, implementions must not break memory safety,
pub trait Device: Any + Send + Sync {
  // It's OK to side have effect for mmio device
  // Panic for bus error
  fn mem_read(&mut self, addr: AddrInfo, data: &mut [u8]);

  // Behave as if `mem_write_masked` with full mask,
  // but usually have a more optimized implementation
  // Panic for bus error.
  fn mem_write(&mut self, addr: AddrInfo, data: &[u8]);

  // Panic for bus error
  // NOTE: even if the device does not support partial write,
  //   it shall check mask and behave as a full write when mask is all active
  fn mem_write_masked(&mut self, addr: AddrInfo, data: &[u8], mask: &[bool]);
}

impl<T: Device> DeviceExt for T {}
pub trait DeviceExt: Device + Sized {
  fn with_addr(self, addr: u32, size: u32) -> DeviceEntry {
    DeviceEntry {
      base_and_size: (addr, size),
      device: Box::new(self),
    }
  }
}

pub struct RegularMemory {
  data: Vec<u8>,
}

impl RegularMemory {
  pub fn with_size(size: u32) -> Self {
    RegularMemory { data: vec![0; size as usize] }
  }
}

impl Device for RegularMemory {
  fn mem_read(&mut self, addr: AddrInfo, data: &mut [u8]) {
    let mem_data = &self.data[addr.as_range()];
    data.copy_from_slice(mem_data);
  }

  fn mem_write(&mut self, addr: AddrInfo, data: &[u8]) {
    let mem_data = &mut self.data[addr.as_range()];
    mem_data.copy_from_slice(data);
  }

  fn mem_write_masked(&mut self, addr: AddrInfo, data: &[u8], mask: &[bool]) {
    let mem_data = &mut self.data[addr.as_range()];
    memcpy_mask(mem_data, data, mask);
  }
}

fn memcpy_mask(dst: &mut [u8], src: &[u8], mask: &[bool]) {
  for i in 0..mask.len() {
    if mask[i] {
      dst[i] = src[i];
    }
  }
}

pub struct DeviceEntry {
  base_and_size: (u32, u32),
  device: Box<dyn Device>,
}

pub struct AddressSpace {
  devices: Vec<DeviceEntry>,
}

impl AddressSpace {
  pub fn read_mem(&mut self, addr: u32, len: u32, data: &mut [u8]) {
    assert_eq!(len as usize, data.len());
    let Some(device_idx) = self.find_device_idx(addr, len) else {
      panic!("read error (no device found), addr=0x{addr:08x}, len={len}B");
    };

    let dev_entry = &mut self.devices[device_idx];
    let addr = AddrInfo { offset: addr - dev_entry.base_and_size.0, len };

    dev_entry.device.mem_read(addr, data);
  }

  pub fn write_mem(&mut self, addr: u32, len: u32, data: &[u8]) {
    assert_eq!(len as usize, data.len());
    let Some(device_idx) = self.find_device_idx(addr, len) else {
      panic!("write error (no device found), addr=0x{addr:08x}, len={len}B");
    };

    let dev_entry = &mut self.devices[device_idx];
    let addr = AddrInfo { offset: addr - dev_entry.base_and_size.0, len };

    dev_entry.device.mem_write(addr, data);
  }

  pub fn write_mem_masked(&mut self, addr: u32, len: u32, data: &[u8], mask: &[bool]) {
    assert_eq!(len as usize, data.len());
    assert_eq!(len as usize, mask.len());
    let Some(device_idx) = self.find_device_idx(addr, len) else {
      panic!("write error (no device found), addr=0x{addr:08x}, len={len}B");
    };

    let dev_entry = &mut self.devices[device_idx];
    let addr = AddrInfo { offset: addr - dev_entry.base_and_size.0, len };

    dev_entry.device.mem_write_masked(addr, data, mask);
  }

  fn find_device_idx(&self, addr: u32, len: u32) -> Option<usize> {
    for (idx, dev) in self.devices.iter().enumerate() {
      let (base, size) = dev.base_and_size;
      if base <= addr && addr - base < size {
        return if addr - base + len < size {
          Some(idx)
        } else {
          None
        };
      }
    }

    None
  }
}

/// Memory map:
/// - 0x0400_0000 - 0x0600_0000 : framebuffer
/// - 0x2000_0000 - 0xc000_0000 : ddr
/// - 0xc000_0000 - 0xc040_0000 : sram
pub fn create_emu_addrspace() -> AddressSpace {
  const DDR_BASE: u32 = 0x2000_0000;
  const DDR_SIZE: u32 = 0xa000_0000;
  const SRAM_BASE: u32 = 0xc000_0000;
  const SRAM_SIZE: u32 = 0x0040_0000;

  const DISPLAY_BASE: u32 = 0x0400_0000;
  const DISPLAY_SIZE: u32 = 0x0200_0000;

  let devices = vec![
    RegularMemory::with_size(DDR_SIZE).with_addr(DDR_BASE, DDR_SIZE),
    RegularMemory::with_size(SRAM_SIZE).with_addr(SRAM_BASE, SRAM_SIZE),
    FrameBuffer::new().with_addr(DISPLAY_BASE, DISPLAY_SIZE),
  ];
  AddressSpace { devices }
}
