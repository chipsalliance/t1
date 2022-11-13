#include "SimulationContext.h"
#include "exceptions.h"

SimulationContext &SimulationContext::getInstance() {
  static SimulationContext singleton;
  return singleton;
}

SimulationContext::SimulationContext() : isa("rv32gcv", "M"), sim(1 << 30), proc(
  /*isa*/ &isa,
  /*varch*/ fmt::format("vlen:{},elen:{}", consts::vlen_in_bits, consts::elen).c_str(),
  /*sim*/  (simif_t *) &sim,
  /*id*/ 0,
  /*halt on reset*/ true,
  /* endianness*/ memif_endianness_little,
  /*log_file_t*/ nullptr,
  /*sout*/ std::cerr
) {
  CHECK_S(!instantiated) << "SimulationContext should be instantiated only once";
  google::InitGoogleLogging("cosim");
  google::InstallFailureSignalHandler();
  instantiated = true;
}

void SimulationContext::pokeInstruction(svBitVecVal *inst, svBitVecVal *s1, svBitVecVal *s2, svBit *valid) {
  VLOG(4) << fmt::format("[{}] pokeInstruction", getCycle());
  loop_until_se_queue_full();
  auto se = find_se_to_issue();
  *inst = se->inst_bits;
  *s1 = se->rs1_bits;
  *s2 = se->rs2_bits;
  *valid = true;
}

void SimulationContext::instructionFire(const svBitVecVal *index) {
  VLOG(4) << fmt::format("[{}] instructionFire", getCycle());
  auto se = find_se_to_issue();
  se->is_issued = true;
  se->issue_idx = *index;
  LOG(INFO) << fmt::format("[{}] issue to rtl ({}), issue index={}", getCycle(), se->describe_insn(), se->issue_idx);
}

void SimulationContext::initCosim() {
  VLOG(4) << fmt::format("[{}] initCosim", getCycle());
  proc.reset();
  // TODO: remove this line, and use CSR write in the test code to enable this the VS field.
  proc.get_state()->sstatus->write(proc.get_state()->sstatus->read() | SSTATUS_VS);
  sim.store(bin, reset_vector);
  VLOG(0)
      << fmt::format("Simulation Environment Initialized: bin={}, wave={}, reset_vector={:#x}, timeout={}", bin, wave,
                     reset_vector, timeout);
}

void SimulationContext::peekResponse(const svBitVecVal *bits) {
  VLOG(4) << fmt::format("[{}] peekResponse", getCycle());
}

void SimulationContext::pokeCSR(svBitVecVal *vl, svBitVecVal *vStart, svBitVecVal *vlmul, svBitVecVal *vSew,
                                svBitVecVal *vxrm, svBit *vta, svBit *vma, svBit *ignoreException) {
  VLOG(4) << fmt::format("[{}] pokeCSR", getCycle());
}


uint8_t SimulationContext::load(uint64_t address) {
  return *sim.addr_to_mem(address);
}

void SimulationContext::loop_until_se_queue_full() {
  while (spike_event_queue.size() < spike_event_queue_size) {
    try {
      if (auto spike_event = spike_step()) {
        SpikeEvent &se = spike_event.value();
        spike_event_queue.push_front(std::move(se));
      }
    } catch (trap_t &trap) {
      LOG(FATAL) << fmt::format("spike trapped with {}", trap.name());
    }
  }
  VLOG(4) << fmt::format("spike_event_queue is full now, start to simulate.");
}

std::optional<SpikeEvent> SimulationContext::spike_step() {
  auto state = proc.get_state();
  auto fetch = proc.get_mmu()->load_insn(state->pc);
  auto event = create_spike_event(fetch);  // event not empty iff fetch is v inst
  auto &xr = proc.get_state()->XPR;
  if (event) {
    auto &se = event.value();
    VLOG(3) << fmt::format("spike start exec insn ({}) (vl={}, sew={}, lmul={})",
                           se.describe_insn(), se.vl, (int) se.vsew, (int) se.vlmul);
    se.pre_log_arch_changes();
    state->pc = fetch.func(&proc, fetch.insn, state->pc);
    se.log_arch_changes();
  } else {
    state->pc = fetch.func(&proc, fetch.insn, state->pc);
  }

  return event;
}

std::optional<SpikeEvent> SimulationContext::create_spike_event(insn_fetch_t fetch) {
  // create SpikeEvent
  uint32_t opcode = clip(fetch.insn.bits(), 0, 6);
  uint32_t width = clip(fetch.insn.bits(), 12, 14);
  bool is_load_type = opcode == 0b0000111;
  bool is_store_type = opcode == 0b0100111;
  bool v_type = opcode == 0b1010111 && width != 0b111;
  if (is_load_type || is_store_type || v_type) {
    return SpikeEvent{proc, fetch};
  } else {
    return {};
  }
}

uint32_t SimulationContext::clip(uint32_t binary, int a, int b) { return (binary >> a) & ((1 << (b - a + 1)) - 1); }

SpikeEvent *SimulationContext::find_se_to_issue() {
  SpikeEvent *se_to_issue = nullptr;
  for (auto iter = spike_event_queue.rbegin(); iter != spike_event_queue.rend(); iter++) {
    if (!iter->is_issued) {
      se_to_issue = &(*iter);
      break;
    }
  }
  CHECK(se_to_issue) << fmt::format("[{}] all events in to_rtl_queue are is_issued", getCycle());  // TODO: handle this
  return se_to_issue;
}

void SimulationContext::timeoutCheck() {
  if (getCycle() > timeout) {
    LOG(FATAL) << fmt::format("Simulation timeout, t={}", getCycle());
  }
}

void SimulationContext::dpiPeekTL(int channel_id, const svBitVecVal *a_opcode, const svBitVecVal *a_param,
                                  const svBitVecVal *a_size, const svBitVecVal *a_source, const svBitVecVal *a_address,
                                  const svBitVecVal *a_mask, const svBitVecVal *a_data, svBit a_corrupt, svBit a_valid,
                                  svBit d_ready) {

}

void SimulationContext::dpiPokeTL(int channel_id, svBitVecVal *d_opcode, svBitVecVal *d_param, svBitVecVal *d_size,
                                  svBitVecVal *d_source, svBitVecVal *d_sink, svBitVecVal *d_denied,
                                  svBitVecVal *d_data, svBit *d_corrupt, svBit *d_valid, svBit *a_ready) {

}
