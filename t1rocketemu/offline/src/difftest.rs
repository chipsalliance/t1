use common::spike_runner::SpikeRunner;
use std::path::Path;
use tracing::info;

use common::rtl_config::RTLConfig;
use common::CommonArgs;

use crate::dut::Dut;
use crate::json_events::*;

pub struct Difftest {
  runner: SpikeRunner,
  dut: Dut,

  #[allow(dead_code)]
  config: RTLConfig,
}

impl Difftest {
  pub fn new(args: CommonArgs) -> Self {
    let config = RTLConfig { vlen: args.vlen, dlen: args.dlen };
    Self {
      runner: SpikeRunner::new(&args, true),
      dut: Dut::new(Path::new(
        &args.log_file.expect("difftest must be run with a log file"),
      )),
      config,
    }
  }

  pub fn diff(&mut self) -> anyhow::Result<()> {
    self.runner.check_and_clear_fence();

    let event = self.dut.step()?;

    match event {
      JsonEvents::SimulationStart { cycle } => {
        self.runner.cycle = *cycle;
        Ok(())
      }
      JsonEvents::SimulationStop { reason, cycle } => {
        info!("simulation stopped at cycle {}, reason {}", cycle, reason);
        self.runner.cycle = *cycle;
        Ok(())
      }
      JsonEvents::Issue { idx, cycle } => {
        self.runner.cycle = *cycle;
        self.runner.peek_issue(&IssueEvent { idx: *idx, cycle: *cycle })
      }
      JsonEvents::MemoryWrite { mask, data, lsu_idx, address, cycle } => {
        self.runner.cycle = *cycle;
        self.runner.peek_memory_write(&MemoryWriteEvent {
          mask: mask.clone(),
          data: data.clone(),
          lsu_idx: *lsu_idx,
          address: *address,
          cycle: *cycle,
        })
      }
      JsonEvents::LsuEnq { enq, cycle } => {
        self.runner.cycle = *cycle;
        self.runner.update_lsu_idx(&LsuEnqEvent { enq: *enq, cycle: *cycle })
      }
      JsonEvents::VrfWrite { issue_idx, vd, offset, mask, data, lane, cycle } => {
        self.runner.cycle = *cycle;
        self.runner.peek_vrf_write(&VrfWriteEvent {
          issue_idx: *issue_idx,
          vd: *vd,
          offset: *offset,
          mask: mask.clone(),
          data: data.clone(),
          lane: *lane,
          cycle: *cycle,
        })
      }
      JsonEvents::CheckRd { data, issue_idx, cycle } => {
        self.runner.cycle = *cycle;
        self.runner.check_rd(&CheckRdEvent { data: *data, issue_idx: *issue_idx, cycle: *cycle })
      }
      JsonEvents::VrfScoreboard { count, issue_idx, cycle } => {
        self.runner.cycle = *cycle;
        self.runner.vrf_scoreboard(&VrfScoreboardEvent {
          count: *count,
          issue_idx: *issue_idx,
          cycle: *cycle,
        })
      }
    }
  }
}
