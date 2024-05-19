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
	pub fn new(size: usize, elf_file: String, log_file: String, vlen: u32, dlen: u32) -> Self {
		Self {
			spike: SpikeHandle::new(size, Path::new(&elf_file), vlen, dlen),
			dut: Dut::new(Path::new(&log_file)),
		}
	}

	fn peek_issue(&mut self, idx: u32) -> anyhow::Result<()> {
		self.spike.peek_issue(idx).unwrap();

		Ok(())
	}

	fn update_lsu_idx(&mut self, enq: u32) -> anyhow::Result<()> {
		self.spike.update_lsu_idx(enq).unwrap();

		Ok(())
	}

	fn peek_vrf_write_from_lsu(
		&mut self,
		idx: u32,
		vd: u32,
		offset: u32,
		mask: u32,
		data: u32,
		instruction: u32,
		lane: u32,
	) -> anyhow::Result<()> {
		assert!(idx < self.spike.config.dlen / 32);

		self
			.spike
			.peek_vrf_write_from_lsu(lane, vd, offset, mask, data, instruction)
	}

	fn peek_vrf_write_from_lane(
		&mut self,
		idx: u32,
		vd: u32,
		offset: u32,
		mask: u32,
		data: u32,
		instruction: u32,
	) -> anyhow::Result<()> {
		// vrf_write.lane_index < config.lane_number
		assert!(idx < self.spike.config.dlen / 32);

		self
			.spike
			.peek_vrf_write_from_lane(idx, vd, offset, mask, data, instruction)
	}

	fn poke_inst(&mut self) -> anyhow::Result<()> {
		loop {
			let se = self.spike.find_se_to_issue();
			if (se.is_vfence_insn || se.is_exit_insn) && self.spike.to_rtl_queue.len() == 1 {
				if se.is_exit_insn {
					error!("Simulation quit graceful");
					return Err(anyhow::anyhow!("graceful exit"));
				}

				self.spike.to_rtl_queue.pop_front();
			} else {
				break;
			}
		}

		// TODO: remove these, now just for aligning online difftest
		if let Some(se) = self.spike.to_rtl_queue.get(0) {
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
				trace!(
					"DPIPokeInst: poke instruction: pc = {:#x}, inst_bits = {:#x}, inst = {}",
					se.pc,
					se.inst_bits,
					se.disasm
				);
			}
		}
		Ok(())
	}

	pub fn diff(&mut self) -> anyhow::Result<()> {
		self.poke_inst().unwrap();

		let event = self.dut.step()?;

		trace!("Spike {}", event.event);
		match &*event.event {
			"peekTL" => {
				let idx = event.parameter.idx.unwrap();
				// assert!(idx < self.spike.config.dlen / 32);
				let opcode = event.parameter.opcode.unwrap();
				let param = event.parameter.param.unwrap();
				let source = event.parameter.source.unwrap();
				let mask = event.parameter.mask.unwrap();
				let data = event.parameter.data.unwrap();
				let corrupt = event.parameter.corrupt.unwrap();
				let dready = event.parameter.dReady.unwrap();
				// check align
				let addr = event.parameter.address.unwrap();
				let size = event.parameter.size.unwrap();
				if addr % (1 << size) != 0 {
					error!("unaligned access (addr={:08X}, size={})", addr, 1 << size)
				}

				let opcode = Opcode::from_u32(opcode);
				self
					.spike
					.peek_tl(PeekTL {
						idx,
						opcode,
						param,
						size,
						source,
						addr,
						mask,
						data,
						corrupt,
						dready,
					})
					.unwrap();
			}
			"issue" => {
				let idx = event.parameter.idx.unwrap();
				self.peek_issue(idx).unwrap();
			}
			"lsuEnq" => {
				let enq = event.parameter.enq.unwrap();
				self.update_lsu_idx(enq).unwrap();
			}
			"vrfWriteFromLsu" => {
				let idx = event.parameter.idx.unwrap();
				let vd = event.parameter.vd.unwrap();
				let offset = event.parameter.offset.unwrap();
				let mask = event.parameter.mask.unwrap();
				let data = event.parameter.data.unwrap();
				let instruction = event.parameter.instruction.unwrap();
				let lane = event.parameter.lane.unwrap();
				self
					.peek_vrf_write_from_lsu(idx, vd, offset, mask, data, instruction, lane)
					.unwrap();
			}
			"vrfWriteFromLane" => {
				let idx = event.parameter.idx.unwrap();
				let vd = event.parameter.vd.unwrap();
				let offset = event.parameter.offset.unwrap();
				let mask = event.parameter.mask.unwrap();
				let data = event.parameter.data.unwrap();
				let instruction = event.parameter.instruction.unwrap();
				self
					.peek_vrf_write_from_lane(idx, vd, offset, mask, data, instruction)
					.unwrap();
			}
			"inst" => {
				let data = event.parameter.data.unwrap();
				// let vxsat = event.parameter.vxsat.unwrap();
				// let rd_valid = event.parameter.rd_valid.unwrap();
				// let rd = event.parameter.rd.unwrap();
				// let mem = event.parameter.mem.unwrap();

				let se = self.spike.to_rtl_queue.back().unwrap();
				se.record_rd_write(data).unwrap();
				se.check_is_ready_for_commit().unwrap();

				self.spike.to_rtl_queue.pop_back();
			}
			_ => {
				panic!("unknown event: {}", event.event)
			}
		}

		Ok(())
	}
}
