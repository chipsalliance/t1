use common::spike_runner::SpikeRunner;
use std::path::Path;

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
  pub fn new(mut args: CommonArgs) -> Self {
    let config = RTLConfig { vlen: args.vlen, dlen: args.dlen };
    Self {
      runner: SpikeRunner::new(&mut args, true),
      dut: Dut::new(Path::new(
        &args.log_file.expect("difftest must be run with a log file"),
      )),
      config,
    }
  }

  pub fn diff(&mut self) -> anyhow::Result<()> {
    loop {
      let se = self.runner.spike_step();
      if se.is_exit() {
        return Err(anyhow::anyhow!("exit detected"));
      }
      if se.is_rd_written() && se.rd_idx != 0 {
        let event = self.dut.step()?;
        
        match event {
          JsonEvents::RegWrite { addr, data, cycle } => {
            self.runner.cycle = *cycle;
            self.runner.check_reg_write(
              &RegWriteEvent { addr: *addr, data: *data, cycle: *cycle },
              &se,
            )?
          }
          JsonEvents::SimulationStop { reason, cycle } => {
            return Err(anyhow::anyhow!("[{}] simulation stop: {}", *cycle, *reason));
          }
        }
      }
    }
  }
}
