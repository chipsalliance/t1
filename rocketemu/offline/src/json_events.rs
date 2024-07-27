use common::spike_runner::SpikeRunner;
use serde::Deserialize;
use spike_rs::spike_event::SpikeEvent;
use tracing::info;

#[derive(Deserialize, Debug)]
#[serde(tag = "event")]
pub(crate) enum JsonEvents {
  RegWrite { addr: u32, data: u32, cycle: u64 },
  SimulationStop { reason: u8, cycle: u64 },
}

pub struct RegWriteEvent {
  pub addr: u32,
  pub data: u32,
  pub cycle: u64,
}

pub(crate) trait JsonEventRunner {
  fn check_reg_write(&mut self, reg_write: &RegWriteEvent, se: &SpikeEvent) -> anyhow::Result<()>;
}

impl JsonEventRunner for SpikeRunner {
  fn check_reg_write(&mut self, reg_write: &RegWriteEvent, se: &SpikeEvent) -> anyhow::Result<()> {
    let addr = reg_write.addr;
    let data = reg_write.data;
    let cycle = reg_write.cycle;

    info!("[{cycle}] RegWrite: idx={addr:02x}, data={data:08x}",);
    info!(
      "[{cycle}] SpikeEvent: idx={:02x}, data={:08x}",
      se.rd_idx, se.rd_bits
    );
    assert_eq!(addr, se.rd_idx, "addr should be equal to se.rd_idx");
    assert_eq!(data, se.rd_bits, "data should be equal to se.rd_bits");

    Ok(())
  }
}
