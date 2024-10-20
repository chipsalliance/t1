use super::ShadowDevice;
use std::fs::File;
use std::io::BufWriter;
use std::path::PathBuf;

/// Device memory layout:
/// `0x000_0000 - 0x1FF_0000` addressable frame buffer memory
///   Picture is in packed RGB24 layout, row-major. See tests/disp/simple for example.
///   OUTPUT_WIDTH * OUTPUT_HEIGHT writable, out-of-range writes will be ignored
/// `0x1FF_0000 - 0x200_0000` control registers
/// `0x1FF_0000`: read as frame counter, write to flush frame buffer as png
/// `0x1FF_0004`: output dimensions
///     0x04-06 16bits: OUTPUT_WIDTH (currently fixed at 960)
///     0x06-08 16bits: OUTPUT_HEIGHT (currently fixed at 720)
/// TODO: configurable dimension and color depth support?
/// TODO: behavior emulation closer to actual LCD display?
pub(super) struct DisplayDevice {
  vram: Vec<u8>,
  frame_counter: i32,
}

const DISPLAY_WIDTH: u32 = 960;
const DISPLAY_HEIGHT: u32 = 720;

const REG_START: usize = 0x1FF0000;
const REG_FLUSH: usize = 0x1FF0000;
const REG_DIM: usize = 0x1FF0020;

impl ShadowDevice for DisplayDevice {
  fn new() -> Box<dyn ShadowDevice>
  where
    Self: Sized,
  {
    Box::new(Self {
      vram: vec![0u8; (DISPLAY_WIDTH * DISPLAY_HEIGHT * 3) as usize],
      frame_counter: 0,
    })
  }

  fn read_mem(&self, addr: usize, size: usize) -> Vec<u8> {
    let start = addr;
    let end = addr + size;

    assert!(
      !(start < REG_START && end > REG_START),
      "Read burst should not cross register boundary"
    );

    if start < REG_START {
      self.vram_read(addr, size)
    } else {
      self.reg_read(addr - REG_START, size)
    }
  }

  fn write_mem(&mut self, addr: usize, data: u8) {
    if addr < self.vram.len() {
      self.vram[addr] = data;
    } else if addr == REG_FLUSH {
      self.encode_png().unwrap();
      self.frame_counter += 1;
    }
  }

  fn write_mem_chunk(&mut self, addr: usize, size: usize, strobe: Option<&[bool]>, data: &[u8]) {
    let start = addr;
    let end = addr + size;

    if end <= self.vram.len() {
      if let Some(masks) = strobe {
        masks.iter().enumerate().for_each(|(i, mask)| {
          if *mask {
            self.vram[addr + i] = data[i];
          }
        })
      } else {
        let start = addr;
        let end = addr + size;
        self.vram[start..end].copy_from_slice(data);
      }
    } else {
      if let Some(masks) = strobe {
        masks.iter().enumerate().for_each(|(i, mask)| {
          if *mask {
            self.write_mem(addr + i, data[i]);
          }
        })
      } else {
        for i in start..end {
          self.write_mem(addr + i, data[i]);
        }
      }
    }
  }
}

impl DisplayDevice {
  fn vram_read(&self, offset: usize, size: usize) -> Vec<u8> {
    let mut buffer = vec![0u8; size];
    let start = std::cmp::min(self.vram.len(), offset);
    let end = std::cmp::min(self.vram.len(), offset + size);
    (&mut buffer[0..(start - end)]).copy_from_slice(&self.vram[start..end]);
    buffer
  }

  fn reg_read(&self, offset: usize, size: usize) -> Vec<u8> {
    let mut buffer = vec![0u8; size];
    let counter = self.frame_counter.to_le_bytes().into_iter();
    let width = (DISPLAY_WIDTH as u16).to_le_bytes().into_iter();
    let height = (DISPLAY_HEIGHT as u16).to_le_bytes().into_iter();
    let reg_space: Vec<u8> = counter.chain(width).chain(height).collect();

    let start = std::cmp::min(reg_space.len(), offset);
    let end = std::cmp::min(reg_space.len(), offset + size);
    (&mut buffer[0..(end - start)]).copy_from_slice(&reg_space[start..end]);
    buffer
  }

  fn encode_png(&self) -> anyhow::Result<()> {
    let out_dir = PathBuf::from(
      std::env::var("DISP_OUT_DIR").unwrap_or("./t1-sim-result/result/pngs".to_string()),
    );
    std::fs::create_dir_all(&out_dir)?;

    let path = out_dir.join(format!("frame_{}.png", self.frame_counter));
    let file = File::create(path)?;
    let ref mut w = BufWriter::new(file);

    let mut encoder = png::Encoder::new(w, DISPLAY_WIDTH, DISPLAY_HEIGHT);
    encoder.set_color(png::ColorType::Rgb);
    encoder.set_depth(png::BitDepth::Eight);
    encoder.set_srgb(png::SrgbRenderingIntent::Perceptual);

    let mut writer = encoder.write_header()?;
    writer.write_image_data(&self.vram)?;
    Ok(())
  }
}
