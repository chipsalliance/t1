#include <fmt/core.h>
#include <fmt/ranges.h>

#include <disasm.h>

#include <decode_macros.h>
#include <verilated.h>

#include "exceptions.h"
#include "spdlog-ext.h"
#include "util.h"
#include "vbridge_impl.h"


/// convert TL style size to size_by_bytes
inline uint32_t decode_size(uint32_t encoded_size) { return 1 << encoded_size; }

inline bool is_pow2(uint32_t n) { return n && !(n & (n - 1)); }



void VBridgeImpl::dpiInitCosim() {
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
    return;
  }

  Log("RecordRFAccess")
      .with("Reg", fmt::format("{}", waddr))
      .with("wdata", fmt::format("{:08X}", wdata))
      .with("pc", fmt::format("{:08X}", pc))
      .warn("rtl detect scalar rf write");

  // find corresponding spike event
  SpikeEvent *se = nullptr;
  for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
    if ((se_iter->pc == pc) && (se_iter->rd_idx == waddr) && (!se_iter->is_committed)) {
      se = &(*se_iter);
      break;
    }
  }
  if (se == nullptr) {
    Log("RecordRFAccess")
        .with("index", waddr)
        .warn("rtl detect sclar rf write which cannot find se, maybe from "
              "committed load insn");
  }
  // start to check RTL rf_write with spike event
  // for non-store ins. check rf write
  // todo: why exclude store insn? store insn shouldn't write regfile., try to remove it
  if ((!se->is_store) && (!se->is_mutiCycle)) {
    CHECK_EQ(wdata, se->rd_new_bits,fmt::format("\n RTL write Reg({})={:08X} but Spike write={:08X}", waddr, wdata, se->rd_new_bits));
  } else if (se->is_mutiCycle) {
    waitforMutiCycleInsn = true;
    pendingInsn_pc = pc;
    pendingInsn_waddr = se->rd_idx;
    pendingInsn_wdata = se->rd_new_bits;
    Log("RecordRFAccess").info("Find MutiCycle Instruction");
  } else {
    Log("RecordRFAccess").info("Find Store insn");
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
      break;
    }
  }

  if (!haveCommittedSe) {
    Log("RecordRFAccess")
        .with("pc", fmt::format("{:08X}", wb_reg_pc))
        .warn("rtl detect rf write which cannot find se");
  }
  // pop the committed Event from the queue
  for (int i = 0; i < to_rtl_queue_size; i++) {
    if (to_rtl_queue.back().is_committed) {
      to_rtl_queue.pop_back();
    }
  }
}

void VBridgeImpl::dpiRefillQueue() {
  if (to_rtl_queue.size() < 2) loop_until_se_queue_full();
}

void VBridgeImpl::loop_until_se_queue_full() {

  while (to_rtl_queue.size() < to_rtl_queue_size) {
    try {
      std::optional<SpikeEvent> spike_event = spike_step();
      if (spike_event.has_value() && !spike_event->is_csr) {
        SpikeEvent &se = spike_event.value();
        to_rtl_queue.push_front(std::move(se));
      }
    } catch (trap_t &trap) {
      FATAL(fmt::format("spike trapped with {}", trap.name()));
    }
  }
  Log("Refilling queue").info("to_rtl_queue is full now, start to simulate");
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

  auto fetch = proc.get_mmu()->load_insn(state->pc);
  auto event = create_spike_event(fetch);
  auto &xr = proc.get_state()->XPR;
  Log("SpikeStep")
      .with("insn", fmt::format("{:08x}", fetch.insn.bits()))
      .with("pc", pc_before)
      .with("disasm", proc.get_disassembler()->disassemble(fetch.insn))
      .info("spike run insn");
  auto &se = event.value();
  se.pre_log_arch_changes();
  pc = fetch.func(&proc, fetch.insn, state->pc);
  se.log_arch_changes();
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
  return event;
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

VBridgeImpl vbridge_impl_instance;
