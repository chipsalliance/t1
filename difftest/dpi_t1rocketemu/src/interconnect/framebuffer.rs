use std::{env, fs::File, io::BufWriter, path::PathBuf};

use crate::interconnect::memcpy_mask;

use super::{AddrInfo, Device, MemResp, MemRespPayload};

/// Device memory layout:
/// `0x000_0000 - 0x1FF_0000` addressable frame buffer memory
///   Picture is in packed RGB24 layout, row-major. See tests/disp/simple for example.
///   DISPLAY_WIDTH * DISPLAY_HEIGHT writable, out-of-range writes will be ignored
/// `0x1FF_0000 - 0x200_0000` control registers
/// `0x1FF_0000`: read as frame counter, write to flush frame buffer as png
/// `0x1FF_0004`: output dimensions
///     0x04-06 16bits: DISPLAY_WIDTH (currently hardcoded)
///     0x06-08 16bits: DISPLAY_HEIGHT (currently hardcoded)
/// TODO: configurable dimension and color depth support?
/// TODO: behavior emulation closer to actual LCD display?

enum Holding {
  WriteDone(u64),
  Reading(u64, AddrInfo),
}

pub struct FrameBuffer {
  vram: Vec<u8>,
  frame_counter: u32,
  holding: Option<Holding>,
}

const DISPLAY_WIDTH: u32 = 960;
const DISPLAY_HEIGHT: u32 = 720;

const REG_START: u32 = 0x1FF0000;

impl FrameBuffer {
  pub fn new() -> Self {
    FrameBuffer {
      vram: vec![0u8; (DISPLAY_WIDTH * DISPLAY_HEIGHT * 3) as usize],
      frame_counter: 0,
      holding: None,
    }
  }

  // save to '{env:DISP_OUT_DIR}/frame_{frame_counter}.png'
  fn save_png(&self) -> anyhow::Result<()> {
    const DEFAULT_DISP_OUT_DIR: &str = "./t1-sim-result/result/pngs";
    let out_dir =
      PathBuf::from(env::var("DISP_OUT_DIR").unwrap_or_else(|_| DEFAULT_DISP_OUT_DIR.into()));

    std::fs::create_dir_all(&out_dir)?;

    let path = out_dir.join(format!("frame_{}.png", self.frame_counter));

    let file = BufWriter::new(File::create(path)?);

    let mut encoder = png::Encoder::new(file, DISPLAY_WIDTH, DISPLAY_HEIGHT);
    encoder.set_color(png::ColorType::Rgb);
    encoder.set_depth(png::BitDepth::Eight);
    encoder.set_srgb(png::SrgbRenderingIntent::Perceptual);

    encoder.write_header()?.write_image_data(&self.vram)?;

    Ok(())
  }

  fn reg_read(&mut self, reg_offset: u32) -> u32 {
    match reg_offset {
      0 => self.frame_counter,
      4 => (DISPLAY_HEIGHT << 16) + DISPLAY_WIDTH,

      _ => panic!(),
    }
  }

  fn reg_write(&mut self, reg_offset: u32, value: u32) {
    let _ = value;
    match reg_offset {
      0 => {
        self.save_png().unwrap();
        self.frame_counter += 1;
      }

      _ => panic!(),
    }
  }

  fn mem_read(&mut self, addr: AddrInfo) -> MemRespPayload<'_> {
    if addr.offset < REG_START {
      // vram access
      assert!(addr.offset + addr.len <= REG_START);
      return MemRespPayload::ReadBuffered(&self.vram[addr.as_range()]);
    }

    // register access

    // allows only 4-byte aligned access
    assert_eq!(4, addr.len);
    assert!(addr.offset % 4 == 0);

    let value = self.reg_read(addr.offset - REG_START);
    MemRespPayload::ReadRegister(u32::to_le_bytes(value))
  }

  fn mem_write(&mut self, addr: AddrInfo, data: &[u8], mask: Option<&[bool]>) {
    if addr.offset < REG_START {
      // vram access
      assert!(addr.offset + addr.len <= REG_START);

      memcpy_mask(&mut self.vram[addr.as_range()], data, mask);
      return;
    }

    // register access

    // allows only 4-byte aligned access
    assert_eq!(4, addr.len);
    assert!(addr.offset % 4 == 0);
    if let Some(m) = mask {
      assert!(m.iter().all(|&x| x));
    }

    let data: &[u8; 4] = data.try_into().unwrap();
    let value = u32::from_le_bytes(*data);
    self.reg_write(addr.offset - REG_START, value);
  }
}

impl Device for FrameBuffer {
  fn req(&mut self, req: super::MemReq<'_>) -> bool {
    if self.holding.is_some() {
      return false;
    }

    let holding = match req.payload {
      super::MemReqPayload::Read => Holding::Reading(req.id, req.addr),
      super::MemReqPayload::Write(data, mask) => {
        self.mem_write(req.addr, data, mask);
        Holding::WriteDone(req.id)
      }
    };

    self.holding = Some(holding);

    true
  }

  fn resp(&mut self) -> Option<super::MemResp<'_>> {
    let held = self.holding.take()?;
    let (id, payload) = match held {
      Holding::WriteDone(id) => (id, MemRespPayload::WriteAck),
      Holding::Reading(id, addr) => (id, self.mem_read(addr)),
    };
    Some(MemResp { id, payload })
  }

  fn tick(&mut self) {}
}
