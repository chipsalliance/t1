mod dut;
mod spike;

use dut::*;
pub use spike::SpikeHandle;
use std::path::Path;
use tracing::{error, trace};

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

		loop {
			let se = self.spike.find_se_to_issue();
			if (se.is_vfence_insn || se.is_exit_insn) && self.spike.to_rtl_queue.len() == 1 {
				if se.is_exit_insn {
					error!("Simulation quit graceful");
					return Err(anyhow::anyhow!("graceful exit"));
				}

				self.spike.to_rtl_queue.pop_back();
				self.spike.se_to_issue = Some(se);
			} else {
				break;
			}
		}

		// TODO: remove these, now just for aligning online difftest
		if let Some(ref se) = self.spike.se_to_issue {
			// it is ensured there are some other instruction not committed, thus
			// se_to_issue should not be issued
			if se.is_vfence_insn || se.is_exit_insn {
				assert!(
					self.spike.to_rtl_queue.len() > 1,
					"to_rtl_queue are smaller than expected"
				);
				if se.is_exit_insn {
					trace!("DPIPokeInst: exit waiting for fence");
				} else {
					trace!("DPIPokeInst: waiting for fence, no issuing new instruction");
				}
			} else {
				trace!("DPIPokeInst: poke instruction")
			}
		}

		Ok(())
	}
}
