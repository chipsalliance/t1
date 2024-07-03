use common::spike_runner::SpikeRunner;
use std::path::Path;
use tracing::trace;

use common::TestArgs;

use crate::json_events::*;
use crate::dut::Dut;

pub struct Difftest {
  runner: SpikeRunner,
  dut: Dut,
}

impl Difftest {
  pub fn new(args: TestArgs) -> Self {
    Self {
      runner: SpikeRunner::new(&args, true),
      dut: Dut::new(Path::new(&args.log_file.expect("difftest must be run with a log file"))),
    }
  }

  fn peek_issue(&mut self, issue: IssueEvent) -> anyhow::Result<()> {
    self.runner.peek_issue(issue).unwrap();

    Ok(())
  }

  fn update_lsu_idx(&mut self, lsu_enq: LsuEnqEvent) -> anyhow::Result<()> {
    self.runner.update_lsu_idx(lsu_enq).unwrap();

    Ok(())
  }

  fn poke_inst(&mut self) -> anyhow::Result<()> {
    loop {
      let se = self.runner.find_se_to_issue();
      if (se.is_vfence_insn || se.is_exit_insn) && self.runner.to_rtl_queue.len() == 1 {
        if se.is_exit_insn {
          return Ok(());
        }

        self.runner.to_rtl_queue.pop_back();
      } else {
        break;
      }
    }

    // TODO: remove these, now just for aligning online_drive difftest
    if let Some(se) = self.runner.to_rtl_queue.front() {
      // it is ensured there are some other instruction not committed, thus
      // se_to_issue should not be issued
      if se.is_vfence_insn || se.is_exit_insn {
        assert!(
          self.runner.to_rtl_queue.len() > 1,
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
      "peekTL" => {}
      "issue" => {
        let idx = event.parameter.idx.unwrap();
        let cycle = event.parameter.cycle.unwrap();
        self.runner.cycle = cycle;
        self.peek_issue(IssueEvent { idx, cycle }).unwrap();
      }
      "lsuEnq" => {
        let enq = event.parameter.enq.unwrap();
        let cycle = event.parameter.cycle.unwrap();
        self.runner.cycle = cycle;
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
        self.runner.cycle = cycle;
        assert!(idx < self.runner.dlen / 32);

        self
          .runner
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
        self.runner.cycle = cycle;
        assert!(idx < self.runner.dlen / 32);
        self
          .runner
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
        self.runner.cycle = cycle;
        // let vxsat = event.parameter.vxsat.unwrap();
        // let rd_valid = event.parameter.rd_valid.unwrap();
        // let rd = event.parameter.rd.unwrap();
        // let mem = event.parameter.mem.unwrap();

        let se = self.runner.to_rtl_queue.back().unwrap();
        se.record_rd_write(data).unwrap();
        se.check_is_ready_for_commit(cycle).unwrap();

        self.runner.to_rtl_queue.pop_back();
      }
      _ => {
        panic!("unknown event: {}", event.event)
      }
    }

    Ok(())
  }
}
