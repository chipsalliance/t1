use common::spike_runner::SpikeRunner;
use tracing::{debug, error, info, info_span, trace, warn};

use crate::dpi::*;
use crate::OfflineArgs;

pub(crate) struct Driver {
  spike_runner: SpikeRunner,
  wave_path: String,
  pub(crate) dlen: u32,

  timeout: u64,

  // driver state
  last_commit_cycle: u64,
  issued: u64,
}

impl Driver {
  pub(crate) fn new(args: &OfflineArgs) -> Self {
    let mut self_ = Self {
      spike_runner: SpikeRunner::new(&args.common_args, false),
      wave_path: args.wave_path.to_owned(),
      dlen: args.common_args.dlen,
      timeout: args.timeout,
      last_commit_cycle: 0,

      issued: 0,
    };
    self_.spike_runner.load_elf(&args.common_args.elf_file).unwrap();
    self_.start_dump_wave();
    self_
  }

  pub(crate) fn axi_write_high_bandwidth(&mut self, _: &AxiWritePayload) {
    info!("[{}] axi_write_high_bandwidth", get_t());
    // TODO:
  }

  pub(crate) fn axi_read_high_bandwidth(&mut self) -> AxiReadPayload {
    // TODO:
    info!("[{}] axi_read_high_bandwidth", get_t());
    AxiReadPayload { data: vec![], beats: 0 }
  }

  pub(crate) fn axi_write_indexed(&mut self, _: &AxiWriteIndexedPayload) {
    info!("[{}] axi_write_indexed", get_t());
    // TODO:
  }

  pub(crate) fn axi_read_indexed(&mut self) -> AxiReadIndexedPayload {
    info!("[{}] axi_read_indexed", get_t());
    // TODO:
    AxiReadIndexedPayload { data: [0; 256 * 4], beats: 0 }
  }

  pub(crate) fn watchdog(&mut self) -> u8 {
    if get_t() - self.last_commit_cycle > self.timeout {
      error!(
        "[{}] watchdog timeout (last_commit_cycle={})",
        get_t(),
        self.last_commit_cycle
      );
      WATCHDOG_TIMEOUT
    } else {
      trace!("[{}] watchdog continue", get_t());
      WATCHDOG_CONTINUE
    }
  }

  fn start_dump_wave(&mut self) {
    dump_wave(&self.wave_path);
  }

  pub(crate) fn issue_instruction(&mut self) -> IssueData {
    let se = self.spike_runner.find_se_to_issue();

    if se.is_vfence_insn() {
      if self.spike_runner.to_rtl_queue.len() == 1 {
        // earlier instructions are committed
        if se.is_exit_insn() {
          info!(
            "[{}] seeing an exit instruction on {:08x}, sending ISSUE_EXIT",
            get_t(),
            se.pc
          );
          self.spike_runner.to_rtl_queue.pop_back();
          IssueData { meta: ISSUE_EXIT, ..Default::default() }
        } else {
          self.spike_runner.to_rtl_queue.pop_back();
          self.issue_instruction() // pop and continue
        }
      } else {
        debug!(
          "[{}] waiting for queue being cleared to issue the fence, len={}",
          get_t(),
          self.spike_runner.to_rtl_queue.len()
        );
        // waiting for earlier instructions to be committed
        IssueData { meta: ISSUE_FENCE, ..Default::default() }
      }
    } else {
      info!(
        "[{}] issuing {} (pc={:08x}, bits={:08x})",
        get_t(),
        se.disasm,
        se.pc,
        se.inst_bits
      );
      self.issued += 1;
      // not a fence, issue it
      IssueData {
        instruction_bits: se.inst_bits,
        src1_bits: se.rs1_bits,
        src2_bits: se.rs2_bits,
        vtype: se.vl,
        vl: se.vl,
        vstart: se.vstart as u32,
        vcsr: se.vcsr(),
        meta: ISSUE_VALID,
      }
    }
  }

  pub(crate) fn retire_instruction(&mut self, _: &Retire) {
    let queue = &mut self.spike_runner.to_rtl_queue;
    assert!(!queue.is_empty() && !queue.back().unwrap().is_vfence_insn());
    info!(
      "[{}] retire_instruction {} (pc={:08x}, bits={:08x})",
      get_t(),
      queue.back().unwrap().disasm,
      queue.back().unwrap().pc,
      queue.back().unwrap().inst_bits,
    );
    queue.pop_back();
    self.last_commit_cycle = get_t();
  }
}
