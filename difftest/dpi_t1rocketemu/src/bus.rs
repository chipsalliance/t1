mod disp;
mod mem;

use disp::*;
use mem::*;
use tracing::{debug, error, trace};

trait ShadowDevice: Send + Sync {
  fn new() -> Box<dyn ShadowDevice>
  where
    Self: Sized;
  /// addr: offset respect to the base of this device
  fn read_mem(&self, addr: usize, size: usize) -> Vec<u8>;
  /// addr: offset respect to the base of this device
  fn write_mem(&mut self, addr: usize, data: u8);
  /// addr: offset respect to the base of this device
  /// strobe: signals which element in data is valid, None = all valid
  fn write_mem_chunk(&mut self, addr: usize, size: usize, strobe: Option<&[bool]>, data: &[u8]);
}

struct ShadowBusDevice {
  base: usize,
  size: usize,
  device: Box<dyn ShadowDevice>,
}

const MAX_DEVICES: usize = 4;

pub(crate) struct ShadowBus {
  devices: [ShadowBusDevice; MAX_DEVICES],
}

impl ShadowBus {
  /// Initiate the devices on the bus as specified in `tests/t1.ld`
  /// NOTE: For some reason DDR is not aligned in the address space
  pub fn new() -> Self {
    const DDR_SIZE: usize = 0x80000000;
    const SCALAR_SIZE: usize = 0x20000000;
    const SRAM_SIZE: usize = 0x00400000;

    Self {
      devices: [
        ShadowBusDevice {
          base: 0x04000000,
          size: 0x02000000,
          device: DisplayDevice::new(),
        },
        ShadowBusDevice {
          base: 0x20000000,
          size: SCALAR_SIZE,
          device: MemDevice::<SCALAR_SIZE>::new(),
        },
        ShadowBusDevice {
          base: 0x40000000,
          size: DDR_SIZE,
          device: MemDevice::<DDR_SIZE>::new(),
        },
        ShadowBusDevice {
          base: 0xc0000000,
          size: SRAM_SIZE,
          device: MemDevice::<SRAM_SIZE>::new(),
        },
      ],
    }
  }

  // size: 1 << arsize
  // nr_beat: arlen + 1
  // bus_size: AXI bus width in bytes
  // return: Vec<u8> with len=bus_size
  // if `size < bus_size`, the result is padded due to AXI narrow transfer rules
  // if `size < bus_size`, then `nr_beat == 1` must hold, narrow burst is not supported
  pub fn read_mem_axi(&self, addr: u32, size: u32, bus_size: u32, nr_beat: u32) -> Vec<u8> {
    assert!(
      addr % size == 0 && bus_size % size == 0,
      "unaligned access addr={addr:#x} size={size}B dlen={bus_size}B"
    );

    assert!(nr_beat > 0);
    assert!(
      nr_beat == 1 || size == bus_size,
      "narrow burst is not supported"
    );

    let start = addr as usize;
    let end = (addr + size) as usize;

    let handler = self.devices.iter().find(|d| match d {
      ShadowBusDevice { base, size, device: _ } => *base <= start && end <= (*base + *size),
    });

    match handler {
      Some(ShadowBusDevice { base, size: _, device }) => {
        let offset = start - *base;
        let data = device.read_mem(offset, (size * nr_beat) as usize);

        if size < bus_size {
          let mut data_padded = vec![0; bus_size as usize];
          let start = (addr % bus_size) as usize;
          let end = start + data.len();
          data_padded[start..end].copy_from_slice(&data);

          data_padded
        } else {
          data
        }
      }
      None => {
        panic!("read addr={addr:#x} size={size}B dlen={bus_size}B leads to nowhere!");
        vec![0; bus_size as usize]
      }
    }
  }

  // size: 1 << awsize
  // bus_size: AXI bus width in bytes
  // masks: write strobes, len=bus_size
  // data: write data, len=bus_size
  pub fn write_mem_axi(
    &mut self,
    addr: u32,
    size: u32,
    bus_size: u32,
    masks: &[bool],
    data: &[u8],
  ) {
    assert!(
      addr % size == 0 && bus_size % size == 0,
      "unaligned write access addr={addr:#x} size={size}B dlen={bus_size}B"
    );

    if !masks.iter().any(|x| *x) {
      trace!("Mask 0 write detected");
      return;
    }

    let start = (addr & ((!bus_size) + 1)) as usize;
    let end = start + bus_size as usize;

    let handler = self.devices.iter_mut().find(|d| match d {
      ShadowBusDevice { base, size, device: _ } => *base <= start && end <= (*base + *size),
    });

    match handler {
      Some(ShadowBusDevice { base, size: _, device }) => {
        let offset = start - *base;
        device.write_mem_chunk(offset, bus_size as usize, Option::from(masks), data);
      }
      None => {
        panic!("write addr={addr:#x} size={size}B dlen={bus_size}B leads to nowhere!");
      }
    }
  }

  pub fn load_mem_seg(&mut self, vaddr: usize, data: &[u8]) {
    let handler = self
      .devices
      .iter_mut()
      .find(|d| match d {
        ShadowBusDevice { base, size, device: _ } => {
          *base <= vaddr as usize && (vaddr as usize + data.len()) <= (*base + *size)
        }
      })
      .unwrap_or_else(|| {
        panic!(
          "fail reading ELF into mem with vaddr={:#x}, len={}B: load memory to nowhere",
          vaddr,
          data.len()
        )
      });

    let offset = vaddr - handler.base;
    handler.device.write_mem_chunk(offset, data.len(), None, data)
  }
}
