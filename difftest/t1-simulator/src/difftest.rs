mod dut;
mod spike;

use dut::*;
pub use spike::SpikeHandle;
use std::path::Path;

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
    timeout: usize,
    set: String,
  ) -> Self {
    Self {
      spike: SpikeHandle::new(size, Path::new(&elf_file), vlen, dlen, timeout, set),
      dut: Dut::new(Path::new(&log_file)),
    }
  }

  pub fn diff(&mut self) -> anyhow::Result<()> {
    self.spike.poke_inst().unwrap();

    let event = self.dut.step()?;

    let cycle = event.parameter.cycle.unwrap();
    self.spike.cycle = cycle;
    self.spike.timeout_check();

    match &*event.event {
      "memoryWrite" => {
        let data = event.parameter.data.clone().unwrap();
        let mask = event.parameter.mask.clone().unwrap();
        let address = event.parameter.address.unwrap();
        let source = event.parameter.source.unwrap();
        self.spike.peek_memory_write(MemoryWriteEvent {
          mask,
          data,
          source,
          address,
          cycle,
        })
      }
      "issue" => {
        let idx = event.parameter.idx.unwrap();
        self.spike.peek_issue(IssueEvent { idx, cycle })
      }
      "lsuEnq" => {
        let enq = event.parameter.enq.unwrap();
        self.spike.update_lsu_idx(LsuEnqEvent { enq, cycle })
      }
      "vrfWriteFromLsu" => {
        let idx = event.parameter.idx.unwrap();
        let vd = event.parameter.vd.unwrap();
        let offset = event.parameter.offset.unwrap();
        let mask = event.parameter.mask.clone().unwrap();
        let data = event.parameter.data.clone().unwrap();
        let instruction = event.parameter.instruction.unwrap();
        let lane = event.parameter.lane.unwrap();
        assert!(idx < self.spike.config.dlen / 32);

        assert!(data.len() <= 4, "data length should be less than 4");
        let mut data_array = [0u8; 4];
        data
          .iter()
          .enumerate()
          .for_each(|(i, &byte)| data_array[i] = byte);
        let data = u32::from_le_bytes(data_array);
        // convert mask to u8
        let mask = mask
          .iter()
          .rev()
          .fold(0, |acc, &bit| (acc << 1) | bit as u8);

        self.spike.peek_vrf_write_from_lsu(VrfWriteEvent {
          idx: lane.trailing_zeros(),
          vd,
          offset,
          mask,
          data,
          instruction,
          cycle,
        })
      }
      "vrfWriteFromLane" => {
        let idx = event.parameter.idx.unwrap();
        let vd = event.parameter.vd.unwrap();
        let offset = event.parameter.offset.unwrap();
        let mask = event.parameter.mask.clone().unwrap();
        let data = event.parameter.data.clone().unwrap();
        let instruction = event.parameter.instruction.unwrap();
        assert!(idx < self.spike.config.dlen / 32);

        assert!(data.len() <= 4, "data length should be less than 4");
        let mut array = [0u8; 4];
        data
          .iter()
          .enumerate()
          .for_each(|(i, &byte)| array[i] = byte);
        let data = u32::from_le_bytes(array);
        // convert mask to u8
        let mask = mask
          .iter()
          .rev()
          .fold(0, |acc, &bit| (acc << 1) | bit as u8);

        self.spike.peek_vrf_write_from_lane(VrfWriteEvent {
          idx,
          vd,
          offset,
          mask,
          data,
          instruction,
          cycle,
        })
      }
      "inst" => {
        let data = event.parameter.data.clone().unwrap();
        // let vxsat = event.parameter.vxsat.unwrap();
        // let rd_valid = event.parameter.rd_valid.unwrap();
        // let rd = event.parameter.rd.unwrap();
        // let mem = event.parameter.mem.unwrap();

        assert!(data.len() <= 4, "data length should be less than 4");
        let mut array = [0u8; 4];
        data
          .iter()
          .enumerate()
          .for_each(|(i, &byte)| array[i] = byte);
        let data = u32::from_le_bytes(array);

        let se = self.spike.to_rtl_queue.back().unwrap();
        se.record_rd_write(data).unwrap();
        se.check_is_ready_for_commit(cycle).unwrap();

        self.spike.last_commit_time = cycle;
        self.spike.to_rtl_queue.pop_back();
        Ok(())
      }
      _ => {
        panic!("unknown event: {}", event.event)
      }
    }
  }
}
