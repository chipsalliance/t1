use serde::Deserialize;
use common::spike_runner::SpikeRunner;
use libspike_rs::spike_event::SpikeEvent;
use tracing::info;

#[derive(Deserialize, Debug, PartialEq, Clone)]
pub enum Opcode {
  PutFullData = 0,
  PutPartialData = 1,
  Get = 4,
  // AccessAckData = 0,
  // AccessAck = 0,
}

#[derive(Deserialize, Debug)]
pub struct EventParameter {
  pub idx: Option<u32>,
  pub enq: Option<u32>,
  pub opcode: Option<u32>,
  pub param: Option<u32>,
  pub size: Option<usize>,
  pub source: Option<u16>,
  pub address: Option<u32>,
  pub mask: Option<u32>,
  pub data: Option<u64>,
  pub corrupt: Option<u32>,
  pub dready: Option<u8>,
  pub vd: Option<u32>,
  pub offset: Option<u32>,
  pub instruction: Option<u32>,
  pub lane: Option<u32>,
  pub vxsat: Option<u32>,
  pub rd_valid: Option<u32>,
  pub rd: Option<u32>,
  pub mem: Option<u32>,
  pub cycle: Option<usize>,
}

#[derive(Deserialize, Debug)]
pub struct JsonEvents {
  pub event: String,
  pub parameter: EventParameter,
}

pub struct IssueEvent {
  pub idx: u32,
  pub cycle: usize,
}

pub struct LsuEnqEvent {
  pub enq: u32,
  pub cycle: usize,
}

pub struct VrfWriteEvent {
  pub idx: u32,
  pub vd: u32,
  pub offset: u32,
  pub mask: u32,
  pub data: u64,
  pub instruction: u32,
  pub cycle: usize,
}
const LSU_IDX_DEFAULT: u8 = 0xff;

pub fn add_rtl_write(se: &mut SpikeEvent, vrf_write: VrfWriteEvent, record_idx_base: usize) {
  (0..4).for_each(|j| {
    if ((vrf_write.mask >> j) & 1) != 0 {
      let written_byte = ((vrf_write.data >> (8 * j)) & 0xff) as u8;
      let record_iter = se
        .vrf_access_record
        .all_writes
        .get_mut(&(record_idx_base + j));

      if let Some(record) = record_iter {
        assert_eq!(
          record.byte,
          written_byte,
          "{j}th byte incorrect ({:02X} != {written_byte:02X}) for vrf \
            write (lane={}, vd={}, offset={}, mask={:04b}) \
            [vrf_idx={}] (lsu_idx={}, disasm: {}, pc: {:#x}, bits: {:#x})",
          record.byte,
          vrf_write.idx,
          vrf_write.vd,
          vrf_write.offset,
          vrf_write.mask,
          record_idx_base + j,
          se.lsu_idx,
          se.disasm,
          se.pc,
          se.inst_bits
        );
        record.executed = true;
      }
    } // end if mask
  }) // end for j
}

pub(crate) trait JsonEventRunner {
  fn peek_issue(&mut self, issue: IssueEvent) -> anyhow::Result<()>;
  fn update_lsu_idx(&mut self, lsu_enq: LsuEnqEvent) -> anyhow::Result<()>;
  fn peek_vrf_write_from_lsu(&mut self, vrf_write: VrfWriteEvent) -> anyhow::Result<()>;
  fn peek_vrf_write_from_lane(&mut self, vrf_write: VrfWriteEvent) -> anyhow::Result<()>;
}

impl JsonEventRunner for SpikeRunner {
  fn peek_issue(&mut self, issue: IssueEvent) -> anyhow::Result<()> {
    let se = self.to_rtl_queue.front_mut().unwrap();
    if se.is_vfence_insn || se.is_exit_insn {
      return Ok(());
    }

    se.is_issued = true;
    se.issue_idx = issue.idx as u8;

    info!(
      "[{}] SpikePeekIssue: idx={}, pc={:#x}, inst={}",
      issue.cycle, issue.idx, se.pc, se.disasm
    );

    Ok(())
  }

  fn update_lsu_idx(&mut self, lsu_enq: LsuEnqEvent) -> anyhow::Result<()> {
    let enq = lsu_enq.enq;
    assert!(enq > 0, "enq should be greater than 0");
    let cycle = lsu_enq.cycle;

    if let Some(se) = self
      .to_rtl_queue
      .iter_mut()
      .rev()
      .find(|se| se.is_issued && (se.is_load || se.is_store) && se.lsu_idx == LSU_IDX_DEFAULT)
    {
      let index = enq.trailing_zeros() as u8;
      se.lsu_idx = index;
      info!(
        "[{cycle}] UpdateLSUIdx: Instruction is allocated with pc: {:#x}, inst: {} \
        and lsu_idx: {index}",
        se.pc, se.disasm
      );
    }
    Ok(())
  }

  fn peek_vrf_write_from_lsu(&mut self, vrf_write: VrfWriteEvent) -> anyhow::Result<()> {
    let cycle = vrf_write.cycle;
    let vlen_in_bytes = self.vlen / 8;
    let lane_number = self.dlen / 32;
    let record_idx_base = (vrf_write.vd * vlen_in_bytes
      + (vrf_write.idx + lane_number * vrf_write.offset) * 4) as usize;

    if let Some(se) = self
      .to_rtl_queue
      .iter_mut()
      .rev()
      .find(|se| se.issue_idx == vrf_write.instruction as u8)
    {
      info!(
        "[{cycle}] RecordRFAccesses: lane={}, vd={}, offset={}, mask={:04b}, data={:08x}, \
        instruction={}, rtl detect vrf queue write",
        vrf_write.idx,
        vrf_write.vd,
        vrf_write.offset,
        vrf_write.mask,
        vrf_write.data,
        vrf_write.instruction
      );

      add_rtl_write(se, vrf_write, record_idx_base);
      return Ok(());
    }

    panic!(
      "[{cycle}] cannot find se with issue_idx={}",
      vrf_write.instruction
    )
  }

  fn peek_vrf_write_from_lane(&mut self, vrf_write: VrfWriteEvent) -> anyhow::Result<()> {
    let cycle = vrf_write.cycle;
    let vlen_in_bytes = self.vlen / 8;
    let lane_number = self.dlen / 32;
    let record_idx_base = (vrf_write.vd * vlen_in_bytes
      + (vrf_write.idx + lane_number * vrf_write.offset) * 4) as usize;

    if let Some(se) = self
      .to_rtl_queue
      .iter_mut()
      .rev()
      .find(|se| se.issue_idx == vrf_write.instruction as u8)
    {
      if !se.is_load {
        info!(
          "[{cycle}] RecordRFAccesses: lane={}, vd={}, offset={}, mask={:04b}, data={:08x}, \
          instruction={}, rtl detect vrf write",
          vrf_write.idx,
          vrf_write.vd,
          vrf_write.offset,
          vrf_write.mask,
          vrf_write.data,
          vrf_write.instruction
        );

        add_rtl_write(se, vrf_write, record_idx_base);
      }
      return Ok(());
    }

    info!(
      "[{cycle}] RecordRFAccess: index={} rtl detect vrf write which cannot find se, \
      maybe from committed load insn",
      vrf_write.idx
    );
    Ok(())
  }
}
