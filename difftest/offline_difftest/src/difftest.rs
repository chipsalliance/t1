mod dut;
mod spike;

use dut::*;
pub use spike::SpikeHandle;
use std::path::Path;
use tracing::error;

pub struct Difftest {
	spike: SpikeHandle,
	dut: Dut,
}

impl Difftest {
	pub fn new(size: usize, elf_file: String, log_file: String, vlen: u32) -> Self {
		Self {
			spike: SpikeHandle::new(size, Path::new(&elf_file), vlen),
			dut: Dut::new(Path::new(&log_file)),
		}
	}

	fn peek_issue(&mut self, idx: u32) -> anyhow::Result<()> {
		self.spike.peek_issue(idx).unwrap();

		Ok(())
	}

	pub fn diff(&mut self) -> anyhow::Result<()> {
		let event = self.dut.step()?;


		match &*event.event {
			"peekTL" => {
				// check align
				let addr = event.parameter.address.unwrap() as u128;
				let size = event.parameter.size.unwrap();
				if addr % (1 << size) != 0 {
					error!("unaligned access (addr={:08X}, size={})", addr, 1 << size)
				}
			}
			"issue" => {
				let idx = event.parameter.idx.unwrap();
				self.peek_issue(idx).unwrap();
			}
			_ => {}
		}

		Ok(())
	}
}
