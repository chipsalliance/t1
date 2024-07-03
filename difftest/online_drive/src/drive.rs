use std::process::exit;
use tracing::{info, info_span, trace};
use common::spike_runner::SpikeRunner;
use common::TestArgs;

use crate::dpi::{AxiReadIndexedPayload, AxiReadPayload, AxiWriteIndexedPayload, AxiWritePayload, IssueData, Retire};

pub(crate) struct Driver {
  spike_runner: SpikeRunner,
  is_finished: bool,
  pub(crate) dlen: u32,
  pub(crate) vlen: u32,
}

impl Driver {
  pub(crate) fn new(args: &TestArgs) -> Self {
    let mut self_ = Self {
      spike_runner: SpikeRunner::new(args, false),
      is_finished: false,
      dlen: args.dlen,
      vlen: args.vlen,
    };
    self_.spike_runner.load_elf(&args.elf_file).unwrap();
    self_
  }

  pub(crate) fn axi_write_high_bandwidth(&mut self, _: &AxiWritePayload)  {
    info_span!("axi_write_high_bandwidth");
    // TODO:
  }

  pub(crate) fn axi_read_high_bandwidth(&mut self) -> AxiReadPayload {
    // TODO:
    info_span!("axi_read_high_bandwidth");
    AxiReadPayload{
      data: vec![],
      beats: 0,
    }
  }

  pub(crate) fn axi_write_indexed(&mut self, _: &AxiWriteIndexedPayload) {
    info_span!("axi_write_indexed");
    // TODO:
  }

  pub(crate) fn axi_read_indexed(&mut self) -> AxiReadIndexedPayload {
    info_span!("axi_read_indexed");
    // TODO:
    AxiReadIndexedPayload{ data: [0; 256 * 4], beats: 0 }
  }

  pub(crate) fn watchdog(&mut self) -> u8 {
    info!("watchdog");
    if self.is_finished {
      0
    } else {
      1
    }
  }

  pub(crate) fn issue_instruction(&mut self) -> IssueData {
    let se = self.spike_runner.find_se_to_issue();
    if se.is_exit_insn() {
      info!("(un)gracefully exiting simulation on exit instruction at {:08x}", se.pc);
      // TODO: set is_finished and returns an empty issue
      exit(0)
    }
    info!("issue_vector_instruction: bits={:08x}, disasm={}", se.inst_bits, se.disasm);
    IssueData {
      instruction_bits: se.inst_bits,
      src1_bits: se.rs1_bits,
      src2_bits: se.rs2_bits,
      vtype: se.vl,
      vl: se.vl,
      vstart: se.vstart as u32,
      vcsr: se.vcsr(),
    }
  }

  pub(crate) fn retire_instruction(&mut self, _: &Retire) {
    info!("retire_instruction");
    self.spike_runner.to_rtl_queue.pop_back();
  }
}
