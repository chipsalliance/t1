#include <fmt/core.h>
#include <fmt/ranges.h>
#include <glog/logging.h>

#include <disasm.h>

#include <decode_macros.h>
#include <verilated.h>

#include "exceptions.h"
#include "spdlog-ext.h"
#include "util.h"
#include "vbridge_impl.h"
#include "glog_exception_safe.h"

/// convert TL style size to size_by_bytes
inline uint32_t decode_size(uint32_t encoded_size) { return 1 << encoded_size; }

inline bool is_pow2(uint32_t n) { return n && !(n & (n - 1)); }



void VBridgeImpl::dpiInitCosim() {
  google::InitGoogleLogging("emulator");
  FLAGS_logtostderr = true;

  LOG(INFO) << fmt::format("VBridgeImpl init cosim");

  proc.reset();
  // TODO: remove this line, and use CSR write in the test code to enable this
  // the VS field.
  proc.get_state()->sstatus->write(proc.get_state()->sstatus->read() |
                                   SSTATUS_VS | SSTATUS_FS);

  auto load_result = sim.load_elf(bin);
  Log("DPIInitCosim")
      .with("config", get_env_arg("COSIM_config"))
      .with("bin", bin)
      .info("Simulation environment initialized");

  proc.get_state()->pc = load_result.entry_addr;

  init_spike();
}

void VBridgeImpl::check_rf_write(svBit ll_wen,
                                svBit rf_wen,
                                svBit wb_valid,
                                svBitVecVal rf_waddr,
                                svBitVecVal rf_wdata,
                                svBitVecVal wb_reg_pc,
                                svBitVecVal wb_reg_inst) {
  if (rf_wen == false)
    return ;
  // peek rtl rf access
  uint32_t waddr = rf_waddr;
  uint64_t wdata_low = rf_wdata;
  uint64_t wdata = wdata_low;
  uint64_t pc = wb_reg_pc;
  uint64_t insn = wb_reg_inst;

  uint8_t opcode = clip(insn, 0, 6);
  bool rtl_csr = opcode == 0b1110011;
//  if(pc=0x11100){
//    LOG(INFO) << fmt::format("RTL csr insn ,pc={:08X}, wbpc={:08X}",pc);
//  }

  // exclude those rtl reg_write from csr insn
  if (rtl_csr) {
    LOG(INFO) << fmt::format("RTL csr insn wirte reg({}) = {:08X}, pc = {:08X}", waddr, wdata, pc);
    return;
  }

  LOG(INFO) << fmt::format("RTL wirte reg({}) = {:08X}, pc = {:08X},DASM={}", waddr, wdata, pc, proc.get_disassembler()->disassemble(wb_reg_inst));

  // find corresponding spike event
  SpikeEvent *se = nullptr;
  for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
    if ((se_iter->pc == pc) && (se_iter->rd_idx == waddr) && (!se_iter->is_committed)) {
      se = &(*se_iter);
      break;
    }
  }
  if (se == nullptr) {
    for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
      LOG(INFO)
          << fmt::format("List: spike pc = {:08X}, write reg({}) from {:08x} to {:08X}, is commit:{}", se_iter->pc,
                         se_iter->rd_idx, se_iter->rd_old_bits, se_iter->rd_new_bits, se_iter->is_committed);
    }
    LOG(FATAL_S)
        << fmt::format("RTL rf_write Cannot find se ; pc = {:08X} , waddr={:08X}, waddr=Reg({})", pc, waddr, waddr);
  }
  // start to check RTL rf_write with spike event
  // for non-store ins. check rf write
  // todo: why exclude store insn? store insn shouldn't write regfile., try to remove it
  if ((!se->is_store) && (!se->is_mutiCycle)) {
    LOG(INFO)
        << fmt::format("Do rf check in pc={:08X},dasm={}",wb_reg_pc,proc.get_disassembler()->disassemble(wb_reg_inst));
    CHECK_EQ_S(wdata, se->rd_new_bits)
        << fmt::format("\n RTL write Reg({})={:08X} but Spike write={:08X}", waddr, wdata, se->rd_new_bits);
  } else if (se->is_mutiCycle) {
    waitforMutiCycleInsn = true;
    pendingInsn_pc = pc;
    pendingInsn_waddr = se->rd_idx;
    pendingInsn_wdata = se->rd_new_bits;
    LOG(INFO) << fmt::format("Find MutiCycle Instruction pc={:08X}", pendingInsn_pc);
  } else {
    LOG(INFO) << fmt::format("Find Store insn");
  }
}

// enter -> check rf write -> commit se -> pop se
void VBridgeImpl::dpiCommitPeek(svBit ll_wen,
                                svBit rf_wen,
                                svBit wb_valid,
                                svBitVecVal rf_waddr,
                                svBitVecVal rf_wdata,
                                svBitVecVal wb_reg_pc,
                                svBitVecVal wb_reg_inst) {
  if (wb_valid == 0 && ll_wen == 0) return;
  bool haveCommittedSe = false;
  uint64_t pc = wb_reg_pc;

  if(ll_wen){
    if (waitforMutiCycleInsn) {
      if(rf_waddr == pendingInsn_waddr && rf_wdata == pendingInsn_wdata){
        waitforMutiCycleInsn = false;
        LOG(INFO) << fmt::format("match mutiCycleInsn pc = {:08x}", pendingInsn_pc);
      }
    }
    return;
  }
//  LOG(INFO) << fmt::format("RTL write back insn {:08X} time:={}", pc, get_t());
  //todo: add this pass_address
//  if (wb_reg_pc == pass_address) { throw ReturnException(); }
  // Check rf write info
  if (rf_wen && (rf_waddr != 0)) {
    check_rf_write(ll_wen, rf_wen, wb_valid, rf_waddr, rf_wdata, wb_reg_pc,
                   wb_reg_inst);
  }

  // set this spike event as committed
  for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
    if (se_iter->pc == pc ) {
      // mechanism to the insn which causes trap.
      // trapped insn will commit with the first insn after trap(0x80000004).
      // It demands the trap insn not to be the last one in the queue.
      if (se_iter->pc == 0x80000004) {
        for (auto se_it = to_rtl_queue.rbegin(); se_it != to_rtl_queue.rend(); se_it++) {
          if (se_it->is_trap) se_it->is_committed = true;
        }
      }
      se_iter->is_committed = true;
      haveCommittedSe = true;
      LOG(INFO) << fmt::format("Set spike {:08X} as committed", se_iter->pc);
      break;
    }
  }

  if (!haveCommittedSe) LOG(INFO) << fmt::format("RTL wb without se in pc =  {:08X}", pc);
  // pop the committed Event from the queue
  for (int i = 0; i < to_rtl_queue_size; i++) {
    if (to_rtl_queue.back().is_committed) {
      LOG(INFO) << fmt::format("Pop SE pc = {:08X} ", to_rtl_queue.back().pc);
      to_rtl_queue.pop_back();
    }
  }
}

void VBridgeImpl::dpiRefillQueue() {
  if (to_rtl_queue.size() < 2) loop_until_se_queue_full();
}

void VBridgeImpl::loop_until_se_queue_full() {
  LOG(INFO) << fmt::format("Refilling Spike queue");
  while (to_rtl_queue.size() < to_rtl_queue_size) {
    try {
      std::optional<SpikeEvent> spike_event = spike_step();
      if (spike_event.has_value() && !spike_event->is_csr) {
        SpikeEvent &se = spike_event.value();
        to_rtl_queue.push_front(std::move(se));
      }
    } catch (trap_t &trap) {
      LOG(FATAL) << fmt::format("spike trapped with {}", trap.name());
    }
  }
  LOG(INFO) << fmt::format("to_rtl_queue is full now, start to simulate.");
  for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
    LOG(INFO) << fmt::format("List: spike pc = {:08X}, write reg({}) from {:08x} to {:08X},commit={}", se_iter->pc,
                             se_iter->rd_idx, se_iter->rd_old_bits, se_iter->rd_new_bits, se_iter->is_committed);
  }
}

// now we take all the instruction as spike event except csr insn
std::optional<SpikeEvent> VBridgeImpl::create_spike_event(insn_fetch_t fetch) {
  return SpikeEvent{proc, fetch, this};
}

// don't creat spike event for csr insn
// todo: haven't created spike event for insn which traps during fetch stage;
// dealing with trap:
// most traps are dealt by Spike when [proc.step(1)];
// traps during fetch stage [fetch = proc.get_mmu()->load_insn(state->pc)] are dealt manually using try-catch block below.
std::optional<SpikeEvent> VBridgeImpl::spike_step() {
  auto state = proc.get_state();
  // to use pro.state, set some csr
  state->dcsr->halt = false;
  // record pc before execute
  auto pc_before = state->pc;
  reg_t pc = state->pc;
  try {
    auto fetch = proc.get_mmu()->load_insn(state->pc);
    auto event = create_spike_event(fetch);
    auto &xr = proc.get_state()->XPR;
    LOG(INFO) << fmt::format("Spike start to execute pc=[{:08X}] insn = {:08X} DISASM:{}", pc_before, fetch.insn.bits(),
                             proc.get_disassembler()->disassemble(fetch.insn));
    auto &se = event.value();
    se.pre_log_arch_changes();
    pc = fetch.func(&proc, fetch.insn, state->pc);
    se.log_arch_changes();

    //

    // Bypass CSR insns commitlog stuff.
    if ((pc & 1) == 0) {
      state->pc = pc;
    } else {
      switch (pc) {
      case PC_SERIALIZE_BEFORE:
        state->serialized = true;
        break;
      case PC_SERIALIZE_AFTER:
        break;
      default:
        Log("SpikeStep")
            .with("pc", fmt::format("{:08x}", pc))
            .fatal("invalid pc");
      }
    }
    // todo: detect exactly the trap
    // if a insn_after_pc = 0x80000004,set it as committed
    // set insn which traps as committed in case the queue stalls
    if (state->pc == 0x80000004) {
      se.is_trap = true;
      LOG(INFO) << fmt::format("Trap happens at pc = {:08X} ", pc_before);
    }
    LOG(INFO) << fmt::format("Spike after execute pc={:08X} ", state->pc);
    return event;
  } catch (trap_t &trap) {
    LOG(INFO) << fmt::format("spike fetch trapped with {}", trap.name());
    proc.step(1);
    LOG(INFO) << fmt::format("Spike mcause={:08X}", state->mcause->read());
    return {};
  } catch (triggers::matched_t &t) {
    LOG(INFO) << fmt::format("spike fetch triggers ");
    proc.step(1);
    LOG(INFO) << fmt::format("Spike mcause={:08X}", state->mcause->read());
    return {};
  }
}



//==================
// end of dpi interfaces
//==================

VBridgeImpl::VBridgeImpl()
    : config(get_env_arg("COSIM_config")),
      varch(fmt::format("vlen:{},elen:{}", config.v_len, config.elen)),
      sim(1l << 32), isa("rv32gcv", "MSU"),
      cfg(/*default_initrd_bounds=*/std::make_pair((reg_t)0, (reg_t)0),
          /*default_bootargs=*/nullptr,
          /*default_isa=*/DEFAULT_ISA,
          /*default_priv=*/DEFAULT_PRIV,
          /*default_varch=*/varch.data(),
          /*default_misaligned=*/false,
          /*default_endianness*/ endianness_little,
          /*default_pmpregions=*/16,
          /*default_mem_layout=*/std::vector<mem_cfg_t>(),
          /*default_hartids=*/std::vector<size_t>(),
          /*default_real_time_clint=*/false,
          /*default_trigger_count=*/4),
      proc(
          /*isa*/ &isa,
          /*cfg*/ &cfg,
          /*sim*/ &sim,
          /*id*/ 0,
          /*halt on reset*/ true,
          /*log_file_t*/ nullptr,
          /*sout*/ std::cerr) {

  DEFAULT_VARCH;
  auto &csrmap = proc.get_state()->csrmap;
  csrmap[CSR_MSIMEND] = std::make_shared<basic_csr_t>(&proc, CSR_MSIMEND, 0);
  proc.enable_log_commits();
}

uint64_t VBridgeImpl::get_t() { return 1; }



uint8_t VBridgeImpl::load(uint64_t address) {
  return *sim.addr_to_mem(address);
}

void VBridgeImpl::init_spike() {
//  proc.reset();
  auto state = proc.get_state();
  LOG(INFO) << fmt::format("Spike reset misa={:08X}", state->misa->read());
  LOG(INFO) << fmt::format("Spike reset mstatus={:08X}", state->mstatus->read());
  // load binary to reset_vector
  LOG(INFO) << fmt::format(
      "Simulation Environment Initialized: COSIM_bin={}",
      bin);
}



VBridgeImpl vbridge_impl_instance;
