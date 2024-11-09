use num_bigint::BigUint;
use serde::{Deserialize, Deserializer};
use spike_rs::runner::SpikeRunner;
use spike_rs::spike_event::LSU_IDX_DEFAULT;
use tracing::{debug, info};

fn bigint_to_vec_u8<'de, D>(deserializer: D) -> Result<Vec<u8>, D::Error>
where
  D: Deserializer<'de>,
{
  let s: &str = Deserialize::deserialize(deserializer)?;
  let bigint = BigUint::parse_bytes(s.trim_start().as_bytes(), 16)
    .ok_or_else(|| serde::de::Error::custom("Failed to parse BigUint from hex string"))?;
  Ok(bigint.to_bytes_le())
}

fn bigint_to_vec_bool<'de, D>(deserializer: D) -> Result<Vec<bool>, D::Error>
where
  D: Deserializer<'de>,
{
  let s: &str = Deserialize::deserialize(deserializer)?;
  let bigint = BigUint::parse_bytes(s.trim_start().as_bytes(), 16)
    .ok_or_else(|| serde::de::Error::custom("Failed to parse BigUint from hex string"))?;
  let bytes = bigint.to_bytes_le();
  let bools = bytes.iter().flat_map(|byte| (0..8).map(move |i| (byte >> i) & 1u8 == 1u8)).collect();

  Ok(bools)
}

fn hex_to_u32<'de, D>(deserializer: D) -> Result<u32, D::Error>
where
  D: Deserializer<'de>,
{
  let s: &str = Deserialize::deserialize(deserializer)?;
  let value =
    u32::from_str_radix(s.trim_start_matches(' '), 16).map_err(serde::de::Error::custom)?;

  Ok(value)
}

fn mask_display(mask: &Vec<bool>) -> String {
  mask.into_iter().map(|&b| if b { '1' } else { '0' }).collect()
}

#[derive(Deserialize, Debug)]
#[serde(tag = "event")]
pub(crate) enum JsonEvents {
  SimulationStart {
    cycle: u64,
  },
  SimulationStop {
    reason: u8,
    cycle: u64,
  },
  Issue {
    idx: u8,
    cycle: u64,
  },
  LsuEnq {
    enq: u32,
    cycle: u64,
  },
  VrfWrite {
    issue_idx: u8,
    vd: u32,
    offset: u32,
    #[serde(deserialize_with = "bigint_to_vec_bool", default)]
    mask: Vec<bool>,
    #[serde(deserialize_with = "bigint_to_vec_u8", default)]
    data: Vec<u8>,
    lane: u32,
    cycle: u64,
  },
  MemoryWrite {
    #[serde(deserialize_with = "bigint_to_vec_bool", default)]
    mask: Vec<bool>,
    #[serde(deserialize_with = "bigint_to_vec_u8", default)]
    data: Vec<u8>,
    lsu_idx: u8,
    #[serde(deserialize_with = "hex_to_u32", default)]
    address: u32,
    cycle: u64,
  },
  CheckRd {
    #[serde(deserialize_with = "hex_to_u32", default)]
    data: u32,
    issue_idx: u8,
    cycle: u64,
  },
  VrfScoreboard {
    count: u32,
    issue_idx: u8,
    cycle: u64,
  },
}

pub struct IssueEvent {
  pub idx: u8,
  pub cycle: u64,
}

pub struct LsuEnqEvent {
  pub enq: u32,
  pub cycle: u64,
}

pub struct VrfWriteEvent {
  pub lane: u32,
  pub vd: u32,
  pub offset: u32,
  pub mask: Vec<bool>,
  pub data: Vec<u8>,
  pub issue_idx: u8,
  pub cycle: u64,
}

pub struct MemoryWriteEvent {
  pub mask: Vec<bool>,
  pub data: Vec<u8>,
  pub lsu_idx: u8,
  pub address: u32,
  pub cycle: u64,
}

pub struct VrfScoreboardEvent {
  pub count: u32,
  pub issue_idx: u8,
  pub cycle: u64,
}

pub struct CheckRdEvent {
  pub data: u32,
  pub issue_idx: u8,
  pub cycle: u64,
}

pub(crate) trait JsonEventRunner {
  fn peek_issue(&mut self, issue: &IssueEvent) -> anyhow::Result<()>;

  fn update_lsu_idx(&mut self, lsu_enq: &LsuEnqEvent) -> anyhow::Result<()>;

  fn peek_vrf_write(&mut self, vrf_write: &VrfWriteEvent) -> anyhow::Result<()>;

  fn vrf_scoreboard(&mut self, report: &VrfScoreboardEvent) -> anyhow::Result<()>;

  fn peek_memory_write(&mut self, memory_write: &MemoryWriteEvent) -> anyhow::Result<()>;

  fn check_and_clear_fence(&mut self);

  fn check_rd(&mut self, check_rd: &CheckRdEvent) -> anyhow::Result<()>;

  fn retire(&mut self, cycle: u64, issue_idx: u8) -> anyhow::Result<()>;
}

impl JsonEventRunner for SpikeRunner {
  fn peek_issue(&mut self, issue: &IssueEvent) -> anyhow::Result<()> {
    self.find_v_se_to_issue(); // ensure the front of queue is a new un-issued se
    let se = self.commit_queue.front_mut().unwrap();
    if se.is_vfence() {
      return Ok(());
    }

    se.issue_idx = issue.idx as u8;

    info!(
      "[{}] SpikePeekIssue: issue_idx={}, pc={:#x}, inst={}",
      issue.cycle, issue.idx, se.pc, se.disasm
    );

    Ok(())
  }

  fn update_lsu_idx(&mut self, lsu_enq: &LsuEnqEvent) -> anyhow::Result<()> {
    let enq = lsu_enq.enq;
    assert!(enq > 0, "enq should be greater than 0");
    let cycle = lsu_enq.cycle;

    if let Some(se) = self
      .commit_queue
      .iter_mut()
      .rev()
      .find(|se| (se.is_vload() || se.is_vstore()) && se.lsu_idx == LSU_IDX_DEFAULT)
    {
      let index = enq.trailing_zeros() as u8;
      se.lsu_idx = index;
      info!(
        "[{cycle}] UpdateLSUIdx: instr ({}) is allocated with lsu_idx: {index}",
        se.describe_insn()
      );
    }
    Ok(())
  }

  fn peek_vrf_write(&mut self, vrf_write: &VrfWriteEvent) -> anyhow::Result<()> {
    let cycle = vrf_write.cycle;
    let vlen_in_bytes = self.vlen / 8;
    let lane_number = self.dlen / 32;
    let record_idx_base = (vrf_write.vd * vlen_in_bytes
      + (vrf_write.lane + lane_number * vrf_write.offset) * 4) as usize;

    let mut retire_issue: Option<u8> = None;

    if let Some(se) =
      self.commit_queue.iter_mut().rev().find(|se| se.issue_idx == vrf_write.issue_idx)
    {
      debug!(
        "[{}] VrfWrite: lane={}, vd={}, idx_base={}, issue_idx={}, offset={}, mask={}, data={:x?} ({})",
        vrf_write.cycle,
        vrf_write.lane,
        record_idx_base,
        vrf_write.vd,
        vrf_write.issue_idx,
        vrf_write.offset,
        mask_display(&vrf_write.mask),
        vrf_write.data,
        se.describe_insn()
      );

      if let Some(unretired_writes) = se.vrf_access_record.unretired_writes {
        assert!(
          unretired_writes > 0,
          "[{}] unretired_writes should be greater than 0, issue_idx={} ({})",
          vrf_write.cycle,
          vrf_write.issue_idx,
          se.describe_insn()
        );
        if unretired_writes == 1 {
          retire_issue = Some(vrf_write.issue_idx);
        }
        se.vrf_access_record.unretired_writes = Some(unretired_writes - 1);
      } else {
        se.vrf_access_record.retired_writes += 1;
      }

      vrf_write.mask.iter().enumerate().filter(|(_, &mask)| mask).for_each(|(offset, _)| {
        let written_byte = *vrf_write.data.get(offset).unwrap_or(&0);

        if let Some(record) = se.vrf_access_record.all_writes.get_mut(&(record_idx_base + offset)) {
          assert_eq!(
            record.byte,
            written_byte,
            "[{}] {offset}th byte incorrect ({:02x} record != {written_byte:02x} written) \
              for vrf write (lane={}, vd={}, offset={}, mask={}, data={:x?}) \
              issue_idx={} [vrf_idx={}] (disasm: {}, pc: {:#x}, bits: {:#x})",
            vrf_write.cycle,
            record.byte,
            vrf_write.lane,
            vrf_write.vd,
            vrf_write.offset,
            mask_display(&vrf_write.mask),
            vrf_write.data,
            se.issue_idx,
            record_idx_base + offset,
            se.disasm,
            se.pc,
            se.inst_bits
          );
          record.executed = true;
        } else {
          debug!(
            "[{}] cannot find vrf write record, maybe not changed (lane={}, vd={}, idx={}, offset={}, mask={}, data={:x?})",
            vrf_write.cycle,
            vrf_write.lane,
            vrf_write.vd,
            record_idx_base + offset,
            vrf_write.offset,
            mask_display(&vrf_write.mask),
            vrf_write.data
          );
        }
      })
    } else {
      info!(
        "[{cycle}] RecordRFAccess: rtl detect vrf write on lane={}, vd={} \
        with no matched se (issue_idx={}), \
        maybe from committed load insn",
        vrf_write.lane, vrf_write.vd, vrf_write.issue_idx
      );
    }

    if let Some(issue_idx) = retire_issue {
      self.retire(cycle, issue_idx).unwrap();
    }

    Ok(())
  }

  fn peek_memory_write(&mut self, memory_write: &MemoryWriteEvent) -> anyhow::Result<()> {
    let data = memory_write.data.to_owned();
    let mask = memory_write.mask.to_owned();
    let cycle = memory_write.cycle;
    let base_addr = memory_write.address;
    let lsu_idx = memory_write.lsu_idx;

    if let Some(se) = self.commit_queue.iter_mut().find(|se| se.lsu_idx == lsu_idx) {
      info!("[{cycle}] MemoryWrite: address={base_addr:08x}, size={}, data={data:x?}, mask={}, pc = {:#x}, disasm = {}", data.len(), mask_display(&mask), se.pc, se.disasm);
      // compare with spike event record
      mask.iter().enumerate()
        .filter(|(_, &mask)| mask)
        .for_each(|(offset, _)| {
          let byte_addr = base_addr + offset as u32;
          let data_byte = *data.get(offset).unwrap_or(&0);
          let mem_write =
            se.mem_access_record.all_writes.get_mut(&byte_addr).unwrap_or_else(|| {
              panic!("[{cycle}] cannot find mem write of byte_addr {byte_addr:08x}")
            });
          let single_mem_write_val = mem_write.writes[mem_write.num_completed_writes].val;
          mem_write.num_completed_writes += 1;
          assert_eq!(single_mem_write_val, data_byte, "[{cycle}] expect mem write of byte {single_mem_write_val:02X}, actual byte {data_byte:02X} (byte_addr={byte_addr:08X}, pc = {:#x}, disasm = {})", se.pc, se.disasm);
        });
      return Ok(());
    }

    panic!("[{cycle}] cannot find se with instruction lsu_idx={lsu_idx}")
  }

  fn vrf_scoreboard(&mut self, report: &VrfScoreboardEvent) -> anyhow::Result<()> {
    let count = report.count;
    let issue_idx = report.issue_idx;
    let cycle = report.cycle;

    let mut should_retire: Option<u8> = None;

    if let Some(se) = self.commit_queue.iter_mut().rev().find(|se| se.issue_idx == issue_idx) {
      assert!(
        se.vrf_access_record.retired_writes <= count,
        "[{cycle}] retired_writes({}) should be less than count({count}), issue_idx={issue_idx} ({})",
        se.vrf_access_record.retired_writes, se.describe_insn()
      );

      // if instruction writes rd, it will retire in check_rd()
      if count == se.vrf_access_record.retired_writes && !se.is_rd_written && !se.is_fd_written {
        should_retire = Some(issue_idx);
      }
      // if all writes are committed, retire the se
      se.vrf_access_record.unretired_writes = Some(count - se.vrf_access_record.retired_writes);

      info!(
        "[{cycle}] VrfScoreboard: count={count}, issue_idx={issue_idx}, retired={} ({})",
        se.vrf_access_record.retired_writes,
        se.describe_insn()
      );
    } else {
      panic!("[{cycle}] cannot find se with instruction issue_idx={issue_idx}");
    }

    if let Some(issue_idx) = should_retire {
      self.retire(cycle, issue_idx).unwrap();
    }

    Ok(())
  }

  /// after update, if instructions before fence are cleared, fence is also cleared
  fn check_and_clear_fence(&mut self) {
    if !self.commit_queue.is_empty() {
      let se = self.commit_queue.back().unwrap();

      if se.is_vfence() && self.commit_queue.len() == 1 {
        self.commit_queue.pop_back();
      }
    }
  }

  fn check_rd(&mut self, check_rd: &CheckRdEvent) -> anyhow::Result<()> {
    let data = check_rd.data;
    let cycle = check_rd.cycle;
    let issue_idx = check_rd.issue_idx;

    let se =
      self.commit_queue.iter_mut().find(|se| se.issue_idx == issue_idx).unwrap_or_else(|| {
        panic!("[{cycle}] cannot find se with instruction issue_idx={issue_idx}")
      });

    info!("[{cycle}] CheckRd: issue_idx={issue_idx}, data={data:x?}");

    se.check_rd(data).expect("Failed to check_rd");

    self.retire(cycle, issue_idx).unwrap();

    Ok(())
  }

  fn retire(&mut self, cycle: u64, issue_idx: u8) -> anyhow::Result<()> {
    if let Some(idx) = self.commit_queue.iter().position(|se| se.issue_idx == issue_idx) {
      if let Some(se) = self.commit_queue.remove(idx) {
        info!(
          "[{cycle}] Retire: retire se with issue_idx={issue_idx}, ({})",
          se.describe_insn()
        );
        se.check_is_ready_for_commit(cycle).unwrap();
      } else {
        panic!("[{cycle}] Retire: cannot remove se with instruction issue_idx={issue_idx}")
      }
    } else {
      panic!("[{cycle}] Retire: cannot find se with instruction issue_idx={issue_idx}")
    }
    Ok(())
  }
}
