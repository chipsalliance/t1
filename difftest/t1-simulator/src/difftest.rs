mod dut;
mod spike;

use dut::*;
pub use spike::SpikeHandle;
use std::path::Path;
use tracing::trace;

pub struct Difftest {
  spike: SpikeHandle,
  dut: Dut,
}

impl Difftest {
  pub fn new(
    size: usize,
    elf_file: String,
    log_file: String,
    vlen: u32,
    dlen: u32,
    set: String,
  ) -> Self {
    Self {
      spike: SpikeHandle::new(size, Path::new(&elf_file), vlen, dlen, set),
      dut: Dut::new(Path::new(&log_file)),
    }
  }

  fn peek_issue(&mut self, issue: IssueEvent) -> anyhow::Result<()> {
    self.spike.peek_issue(issue).unwrap();

    Ok(())
  }

  fn update_lsu_idx(&mut self, lsu_enq: LsuEnqEvent) -> anyhow::Result<()> {
    self.spike.update_lsu_idx(lsu_enq).unwrap();

    Ok(())
  }

  fn poke_inst(&mut self) -> anyhow::Result<()> {
    loop {
      let se = self.spike.find_se_to_issue();
      if (se.is_vfence_insn || se.is_exit_insn) && self.spike.to_rtl_queue.len() == 1 {
        if se.is_exit_insn {
          return Ok(());
        }

        self.spike.to_rtl_queue.pop_back();
      } else {
        break;
      }
    }

    // TODO: remove these, now just for aligning online difftest
    if let Some(se) = self.spike.to_rtl_queue.front() {
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
          "DPIPokeInst: poke instruction: pc={:#x}, inst={}",
          se.pc,
          se.disasm
        );
      }
    }
    Ok(())
  }

  pub fn diff(&mut self) -> anyhow::Result<()> {
    self.poke_inst().unwrap();

    let event = self.dut.step()?;

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
        let dready = event.parameter.dready.unwrap() != 0;
        let cycle = event.parameter.cycle.unwrap();
        self.spike.cycle = cycle;
        // check align
        let addr = event.parameter.address.unwrap();
        let size = (1 << event.parameter.size.unwrap()) as usize;
        assert_eq!(
          addr as usize % size,
          0,
          "[{cycle}] unaligned access (addr={addr:08X}, size={size}"
        );

        let opcode = Opcode::from_u32(opcode);
        self
          .spike
          .peek_tl(&PeekTLEvent {
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
            cycle,
          })
          .unwrap();
      }
      "issue" => {
        let idx = event.parameter.idx.unwrap();
        let cycle = event.parameter.cycle.unwrap();
        self.spike.cycle = cycle;
        self.peek_issue(IssueEvent { idx, cycle }).unwrap();
      }
      "lsuEnq" => {
        let enq = event.parameter.enq.unwrap();
        let cycle = event.parameter.cycle.unwrap();
        self.spike.cycle = cycle;
        self.update_lsu_idx(LsuEnqEvent { enq, cycle }).unwrap();
      }
      "vrfWriteFromLsu" => {
        let idx = event.parameter.idx.unwrap();
        let vd = event.parameter.vd.unwrap();
        let offset = event.parameter.offset.unwrap();
        let mask = event.parameter.mask.unwrap();
        let data = event.parameter.data.unwrap();
        let instruction = event.parameter.instruction.unwrap();
        let lane = event.parameter.lane.unwrap();
        let cycle = event.parameter.cycle.unwrap();
        self.spike.cycle = cycle;
        assert!(idx < self.spike.config.dlen / 32);

        self
          .spike
          .peek_vrf_write_from_lsu(VrfWriteEvent {
            idx: lane.trailing_zeros(),
            vd,
            offset,
            mask,
            data,
            instruction,
            cycle,
          })
          .unwrap();
      }
      "vrfWriteFromLane" => {
        let idx = event.parameter.idx.unwrap();
        let vd = event.parameter.vd.unwrap();
        let offset = event.parameter.offset.unwrap();
        let mask = event.parameter.mask.unwrap();
        let data = event.parameter.data.unwrap();
        let instruction = event.parameter.instruction.unwrap();
        let cycle = event.parameter.cycle.unwrap();
        self.spike.cycle = cycle;
        assert!(idx < self.spike.config.dlen / 32);
        self
          .spike
          .peek_vrf_write_from_lane(VrfWriteEvent {
            idx,
            vd,
            offset,
            mask,
            data,
            instruction,
            cycle,
          })
          .unwrap();
      }
      "inst" => {
        let data = event.parameter.data.unwrap() as u32;
        let cycle = event.parameter.cycle.unwrap();
        self.spike.cycle = cycle;
        // let vxsat = event.parameter.vxsat.unwrap();
        // let rd_valid = event.parameter.rd_valid.unwrap();
        // let rd = event.parameter.rd.unwrap();
        // let mem = event.parameter.mem.unwrap();

        let se = self.spike.to_rtl_queue.back().unwrap();
        se.record_rd_write(data).unwrap();
        se.check_is_ready_for_commit(cycle).unwrap();

        self.spike.to_rtl_queue.pop_back();
      }
      _ => {
        panic!("unknown event: {}", event.event)
      }
    }

    Ok(())
  }
}
