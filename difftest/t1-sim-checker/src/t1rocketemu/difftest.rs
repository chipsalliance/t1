use spike_rs::runner::SpikeRunner;

use super::json_events::*;

pub fn diff(runner: &mut SpikeRunner, event: &JsonEvents) -> anyhow::Result<()> {
  runner.check_and_clear_fence();

  match event {
    JsonEvents::RegWrite { idx, data, cycle } => {
      runner.cycle = *cycle;
      runner.peek_reg_write(&RegWriteEvent { idx: *idx, data: *data, cycle: *cycle })
    }
    JsonEvents::RegWriteWait { idx, cycle } => {
      runner.cycle = *cycle;
      runner.peek_reg_write_wait(&RegWriteWaitEvent { idx: *idx, cycle: *cycle })
    }
    JsonEvents::FregWrite { idx, data, cycle } => {
      runner.cycle = *cycle;
      runner.peek_freg_write(&RegWriteEvent { idx: *idx, data: *data, cycle: *cycle })
    }
    JsonEvents::FregWriteWait { idx, cycle } => {
      runner.cycle = *cycle;
      runner.peek_freg_write_wait(&RegWriteWaitEvent { idx: *idx, cycle: *cycle })
    }
    JsonEvents::Issue { idx, cycle } => {
      runner.cycle = *cycle;
      runner.peek_issue(&IssueEvent { idx: *idx, cycle: *cycle })
    }
    JsonEvents::MemoryWrite { mask, data, lsu_idx, address, cycle } => {
      runner.cycle = *cycle;
      runner.peek_memory_write(&MemoryWriteEvent {
        mask: mask.clone(),
        data: data.clone(),
        lsu_idx: *lsu_idx,
        address: *address,
        cycle: *cycle,
      })
    }
    JsonEvents::LsuEnq { enq, cycle } => {
      runner.cycle = *cycle;
      runner.update_lsu_idx(&LsuEnqEvent { enq: *enq, cycle: *cycle })
    }
    JsonEvents::VrfWrite { issue_idx, vrf_idx, mask, data, cycle } => {
      runner.cycle = *cycle;
      runner.peek_vrf_write(&VrfWriteEvent {
        issue_idx: *issue_idx,
        vrf_idx: *vrf_idx,
        mask: mask.clone(),
        data: data.clone(),
        cycle: *cycle,
      })
    }
    JsonEvents::CheckRd { data, issue_idx, cycle } => {
      runner.cycle = *cycle;
      runner.check_rd(&CheckRdEvent { data: *data, issue_idx: *issue_idx, cycle: *cycle })
    }
    JsonEvents::VrfScoreboard { count, issue_idx, cycle } => {
      runner.cycle = *cycle;
      runner.vrf_scoreboard(&VrfScoreboardEvent {
        count: *count,
        issue_idx: *issue_idx,
        cycle: *cycle,
      })
    }
  }
}
