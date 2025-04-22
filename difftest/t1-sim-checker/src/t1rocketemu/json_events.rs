use anyhow::{anyhow, bail, ensure};
use num_bigint::BigUint;
use serde::{Deserialize, Deserializer};
use spike_rs::runner::SpikeRunner;
use spike_rs::spike_event::LSU_IDX_DEFAULT;
use tracing::{debug, error, info};

fn str_to_vec_u8<'de, D>(deserializer: D) -> Result<Vec<u8>, D::Error>
where
  D: Deserializer<'de>,
{
  let s: &str = Deserialize::deserialize(deserializer)?;
  let bigint = BigUint::parse_bytes(s.trim_start().as_bytes(), 16)
    .ok_or_else(|| serde::de::Error::custom("Failed to parse BigUint from hex string"))?;
  Ok(bigint.to_bytes_le())
}

fn str_to_vec_bool<'de, D>(deserializer: D) -> Result<Vec<bool>, D::Error>
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

fn str_to_u32<'de, D>(deserializer: D) -> Result<u32, D::Error>
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

fn data_display(data: &Vec<u8>) -> String {
  data.into_iter().rev().map(|&b| format! {"{:02x}", b}).collect()
}

#[derive(Deserialize, Debug)]
#[serde(tag = "event")]
pub(crate) enum JsonEvents {
  RegWrite {
    idx: u8,
    #[serde(deserialize_with = "str_to_u32", default)]
    data: u32,
    cycle: u64,
  },
  RegWriteWait {
    idx: u8,
    cycle: u64,
  },
  FregWrite {
    idx: u8,
    #[serde(deserialize_with = "str_to_u32", default)]
    data: u32,
    cycle: u64,
  },
  FregWriteWait {
    idx: u8,
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
    vrf_idx: usize,
    #[serde(deserialize_with = "str_to_vec_bool", default)]
    mask: Vec<bool>,
    #[serde(deserialize_with = "str_to_vec_u8", default)]
    data: Vec<u8>,
    cycle: u64,
  },
  MemoryWrite {
    #[serde(deserialize_with = "str_to_vec_bool", default)]
    mask: Vec<bool>,
    #[serde(deserialize_with = "str_to_vec_u8", default)]
    data: Vec<u8>,
    lsu_idx: u8,
    #[serde(deserialize_with = "str_to_u32", default)]
    address: u32,
    cycle: u64,
  },
  CheckRd {
    #[serde(deserialize_with = "str_to_u32", default)]
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

pub struct RegWriteEvent {
  pub idx: u8,
  pub data: u32,
  pub cycle: u64,
}

pub struct RegWriteWaitEvent {
  pub idx: u8,
  pub cycle: u64,
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
  pub issue_idx: u8,
  pub vrf_idx: usize,
  pub mask: Vec<bool>,
  pub data: Vec<u8>,
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
  fn peek_reg_write(&mut self, reg_write: &RegWriteEvent) -> anyhow::Result<()>;

  fn peek_reg_write_wait(&mut self, reg_write: &RegWriteWaitEvent) -> anyhow::Result<()>;

  fn peek_freg_write(&mut self, reg_write: &RegWriteEvent) -> anyhow::Result<()>;

  fn peek_freg_write_wait(&mut self, reg_write: &RegWriteWaitEvent) -> anyhow::Result<()>;

  fn peek_issue(&mut self, issue: &IssueEvent) -> anyhow::Result<()>;

  fn update_lsu_idx(&mut self, lsu_enq: &LsuEnqEvent) -> anyhow::Result<()>;

  fn peek_vrf_write(&mut self, vrf_write: &VrfWriteEvent) -> anyhow::Result<()>;

  fn vrf_scoreboard(&mut self, vrf_scoreboard: &VrfScoreboardEvent) -> anyhow::Result<()>;

  fn peek_memory_write(&mut self, memory_write: &MemoryWriteEvent) -> anyhow::Result<()>;

  fn check_and_clear_fence(&mut self);

  fn check_rd(&mut self, check_rd: &CheckRdEvent) -> anyhow::Result<()>;

  fn retire(&mut self, cycle: u64, issue_idx: u8) -> anyhow::Result<()>;
}

impl JsonEventRunner for SpikeRunner {
  fn peek_reg_write(&mut self, reg_write: &RegWriteEvent) -> anyhow::Result<()> {
    let cycle = reg_write.cycle;
    let idx = reg_write.idx;
    let data = reg_write.data;

    if let Some(board_data) = self.rf_board[idx as usize] {
      info!(
        "[{cycle}] RegWrite: Hit board! idx={idx}, rtl data={data:#x}, board data={board_data:#x}",
      );

      ensure!(
        data == board_data,
        "[{cycle}] RegWrite: rtl data={data:#x}; board data={board_data:#x}"
      );

      self.rf_board[idx as usize] = None;

      return Ok(());
    }

    let se = self.find_reg_se();

    info!(
      "[{cycle}] RegWrite: rtl idx={idx}, data={data:#x}; se idx={}, data={:#x} ({})",
      se.rd_idx,
      se.rd_bits,
      se.describe_insn()
    );

    ensure!(
      idx as u32 == se.rd_idx,
      "[{cycle}] rtl idx={idx}; se idx={}",
      se.rd_idx
    );
    ensure!(
      data == se.rd_bits,
      "[{cycle}] rtl data={data:#x}; se data={:#x}",
      se.rd_bits
    );

    Ok(())
  }

  fn peek_reg_write_wait(&mut self, reg_write: &RegWriteWaitEvent) -> anyhow::Result<()> {
    let cycle = reg_write.cycle;
    let idx = reg_write.idx;

    let se = self.find_reg_se();

    info!(
      "[{cycle}] RegWriteWait: rtl idx={idx}; se idx={}, data={:#x} ({})",
      se.rd_idx,
      se.rd_bits,
      se.describe_insn()
    );

    ensure!(
      idx as u32 == se.rd_idx,
      "rtl idx({idx}) should be equal to spike idx({})",
      se.rd_idx
    );

    self.rf_board[idx as usize] = Some(se.rd_bits);

    Ok(())
  }

  fn peek_freg_write(&mut self, reg_write: &RegWriteEvent) -> anyhow::Result<()> {
    let cycle = reg_write.cycle;
    let idx = reg_write.idx;
    let data = reg_write.data;

    if let Some(board_data) = self.frf_board[idx as usize] {
      info!(
        "[{cycle}] FregWrite: Hit board! idx={idx}, rtl data={data:#x}, board data={board_data:#x}",
      );

      ensure!(
        data == board_data,
        "rtl data({data:#x}) should be equal to board data({board_data:#x})"
      );

      self.frf_board[idx as usize] = None;

      return Ok(());
    }

    let se = self.find_freg_se();

    info!(
      "[{cycle}] FregWrite: rtl idx={idx}, data={data:#x}; se idx={}, data={:#x} ({})",
      se.rd_idx,
      se.rd_bits,
      se.describe_insn()
    );

    ensure!(
      idx as u32 == se.rd_idx,
      "rtl idx({idx}) should be equal to spike idx({})",
      se.rd_idx
    );
    ensure!(
      data == se.rd_bits,
      "rtl data({data:#x}) should be equal to spike data({:#x})",
      se.rd_bits
    );

    Ok(())
  }

  fn peek_freg_write_wait(&mut self, reg_write: &RegWriteWaitEvent) -> anyhow::Result<()> {
    let cycle = reg_write.cycle;
    let idx = reg_write.idx;

    let se = self.find_freg_se();

    info!(
      "[{cycle}] FregWriteWait: rtl idx={idx}; se idx={}, data={:#x} ({})",
      se.rd_idx,
      se.rd_bits,
      se.describe_insn()
    );

    ensure!(
      idx as u32 == se.rd_idx,
      "rtl idx({idx}) should be equal to spike idx({})",
      se.rd_idx
    );

    self.frf_board[idx as usize] = Some(se.rd_bits);

    Ok(())
  }

  fn peek_issue(&mut self, issue: &IssueEvent) -> anyhow::Result<()> {
    let mut se = self.find_v_se(); // ensure the front of queue is a new un-issued se

    let cycle = issue.cycle;
    let idx = issue.idx;

    se.issue_idx = idx as u8;

    info!("[{cycle}] Issue: issue_idx={idx} ({})", se.describe_insn());

    self.commit_queue.push_front(se);

    Ok(())
  }

  fn update_lsu_idx(&mut self, lsu_enq: &LsuEnqEvent) -> anyhow::Result<()> {
    let enq = lsu_enq.enq;
    ensure!(enq > 0, "enq should be greater than 0");
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

    let mut retire_issue: Option<u8> = None;

    if let Some(se) =
      self.commit_queue.iter_mut().rev().find(|se| se.issue_idx == vrf_write.issue_idx)
    {
      debug!(
        "[{}] VrfWrite: issue_idx={}, idx_base={},  mask={}, data={}({:02x?}) ({})",
        vrf_write.cycle,
        vrf_write.issue_idx,
        vrf_write.vrf_idx,
        mask_display(&vrf_write.mask),
        data_display(&vrf_write.data),
        vrf_write.data,
        se.describe_insn()
      );

    // check if all writes retired
    if let Some(unretired_writes) = se.vrf_access_record.unretired_writes {
      ensure!(
        unretired_writes > 0,
        "[{}] VrfWrite: unretired_writes should be greater than 0, issue_idx={} ({})",
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

        if let Some(record) = se.vrf_access_record.all_writes.get_mut(&(vrf_write.vrf_idx + offset)) {
          assert_eq!(
            record.byte,
            written_byte,
            "[{}] VrfWrite: {offset}th byte incorrect ({:#02x} record != {written_byte:#02x} written) \
              (mask={}, data={:x?}) \
              issue_idx={} [vrf_idx={}] (disasm: {}, pc: {:#x}, bits: {:#x})",
            vrf_write.cycle,
            record.byte,
            mask_display(&vrf_write.mask),
            vrf_write.data,
            se.issue_idx,
            vrf_write.vrf_idx + offset,
            se.disasm,
            se.pc,
            se.inst_bits
          );
          record.executed = true;
        } else {
          debug!(
            "[{}] VrfWrite: cannot find vrf write record, maybe not changed (idx={}, mask={}, data={:x?})",
            vrf_write.cycle,
            vrf_write.vrf_idx + offset,
            mask_display(&vrf_write.mask),
            vrf_write.data
          );
        }
      })
    } else {
      info!(
        "[{cycle}] VrfWrite: rtl detect vrf write on idx={} \
        with no matched se (issue_idx={}), \
        maybe from committed load insn",
        vrf_write.vrf_idx, vrf_write.issue_idx
      );
    }

    if let Some(issue_idx) = retire_issue {
      self.retire(cycle, issue_idx)?;
    }

    Ok(())
  }

  fn peek_memory_write(&mut self, memory_write: &MemoryWriteEvent) -> anyhow::Result<()> {
    let data = memory_write.data.to_owned();
    let mask = memory_write.mask.to_owned();
    let cycle = memory_write.cycle;
    let base_addr = memory_write.address & !((32 / 8) - 1); // align to data width
    let lsu_idx = memory_write.lsu_idx;

    if let Some(se) = self.commit_queue.iter_mut().find(|se| se.lsu_idx == lsu_idx) {
      info!("[{cycle}] MemoryWrite: address={base_addr:#x}, size={}, data={data:x?}, mask={}, pc = {:#x}, disasm = {}", data.len(), mask_display(&mask), se.pc, se.disasm);
      // compare with spike event record
      mask.iter().enumerate()
        .filter(|(_, &mask)| mask)
        .try_for_each(|(offset, _)| {
          let byte_addr = base_addr + offset as u32;
          let data_byte = *data.get(offset).unwrap_or(&0);
          let mem_write = se.mem_access_record.all_writes.get_mut(&byte_addr).ok_or_else(
              || anyhow!("[{cycle}] MemoryWrite: cannot find mem write of byte_addr {byte_addr:#x}")
            )?;
          let single_mem_write_val = mem_write.writes[mem_write.num_completed_writes].val;
          mem_write.num_completed_writes += 1;

          ensure!(single_mem_write_val == data_byte, "[{cycle}] MemoryWrite: expect mem write of byte {single_mem_write_val:#02x}, actual byte {data_byte:#02x} (byte_addr={byte_addr:#x}, pc = {:#x}, disasm = {})", se.pc, se.disasm);

          Ok(())
        })?;

      return Ok(());
    }

    bail!("[{cycle}] MemoryWrite: cannot find se with instruction lsu_idx={lsu_idx}")
  }

  fn vrf_scoreboard(&mut self, vrf_scoreboard: &VrfScoreboardEvent) -> anyhow::Result<()> {
    let count = vrf_scoreboard.count;
    let issue_idx = vrf_scoreboard.issue_idx;
    let cycle = vrf_scoreboard.cycle;

    let mut should_retire: Option<u8> = None;

    if let Some(se) = self.commit_queue.iter_mut().rev().find(|se| se.issue_idx == issue_idx) {
      ensure!(
        se.vrf_access_record.retired_writes <= count,
        "[{cycle}] VrfScoreboard: retired_writes({}) should be less than count({count}), issue_idx={issue_idx} ({})",
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
    } else if count != 0 {
      bail!("[{cycle}] VrfScoreboard: cannot find se with instruction issue_idx={issue_idx}, count={count}");
    }

    if let Some(issue_idx) = should_retire {
      self.retire(cycle, issue_idx)?;
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

    let se = self.commit_queue.iter().find(|se| se.issue_idx == issue_idx).ok_or_else(|| {
      anyhow!("[{cycle}] CheckRd: cannot find se with instruction issue_idx={issue_idx}")
    })?;

    info!("[{cycle}] CheckRd: issue_idx={issue_idx}, data={data:x?}");

    se.check_rd(data).expect("Failed to check_rd");

    self.retire(cycle, issue_idx)?;

    Ok(())
  }

  fn retire(&mut self, cycle: u64, issue_idx: u8) -> anyhow::Result<()> {
    self.commit_queue.clone().into_iter().for_each(|se| {
      debug!(
        "[{cycle}] Retire: there is se with issue_idx={} ({}) in commit queue now",
        se.issue_idx,
        se.describe_insn()
      );
    });

    if let Some(idx) = self.commit_queue.iter().rev().position(|se| se.issue_idx == issue_idx) {
      // use (len - 1 - idx) to get the real idx, a little tricky
      if let Some(se) = self.commit_queue.remove(self.commit_queue.len() - 1 - idx) {
        info!(
          "[{cycle}] Retire: retire se with issue_idx={issue_idx}, ({})",
          se.describe_insn()
        );
        se.check_is_ready_for_commit(cycle)?;
      } else {
        bail!("[{cycle}] Retire: cannot remove se with instruction issue_idx={issue_idx}")
      }
    } else {
      bail!("[{cycle}] Retire: cannot find se with instruction issue_idx={issue_idx}")
    }
    Ok(())
  }
}
