#include <fmt/core.h>
#include <glog/logging.h>

#include "disasm.h"

#include "verilated.h"
#include "verilated_vpi.h"

#include "exceptions.h"
#include "vbridge_impl.h"
#include "vbridge.h"
#include "util.h"
#include "rtl_event.h"
#include "vpi.h"
#include "tl_interface.h"

void TLBank::step() {
  if (remainingCycles > 0) remainingCycles--;
}

[[nodiscard]] bool TLBank::done() const {
  return op != opType::Nil && remainingCycles == 0;
}

[[nodiscard]] bool TLBank::ready() const {
  return op == opType::Nil;
}

void TLBank::clear() {
  op = opType::Nil;
}

TLBank::TLBank() {
  op = opType::Nil;
}

void VBridgeImpl::reset() {
  top.clock = 0;
  top.reset = 1;
  top.eval();
  tfp.dump(0);

  // posedge
  top.clock = 1;
  top.eval();
  tfp.dump(1);

  // negedge
  top.reset = 0;
  top.clock = 0;
  top.eval();
  tfp.dump(2);
  // posedge
  top.reset = 0;
  top.clock = 1;
  top.eval();
  tfp.dump(3);
  ctx.time(2);
}

void VBridgeImpl::setup(const std::string &_bin, const std::string &_wave, uint64_t _reset_vector, uint64_t cycles) {
  this->bin = _bin;
  this->wave = _wave;
  this->reset_vector = _reset_vector;
  this->timeout = cycles;
}

insn_fetch_t VBridgeImpl::fetch_proc_insn() {
  auto state = proc.get_state();
  mmu_t *_mmu = proc.get_mmu();
  auto ic_entry = _mmu->access_icache(state->pc);
  auto fetch = ic_entry->data;
  assert(ic_entry->tag == state->pc);
  return fetch;
}

VBridgeImpl::VBridgeImpl() :
    sim(1 << 30),
    isa("rv32gcv", "M"),
    proc(
        /*isa*/ &isa,
        /*varch*/ "vlen:1024,elen:32",
        /*sim*/ &sim,
        /*id*/ 0,
        /*halt on reset*/ true,
        /*log_file_t*/ nullptr,
        /*sout*/ std::cerr) {}

VBridgeImpl::~VBridgeImpl() {
  terminate_simulator();
}

void VBridgeImpl::configure_simulator(int argc, char **argv) {
  ctx.commandArgs(argc, argv);
}

uint64_t VBridgeImpl::spike_mem_load(uint64_t addr, uint32_t size) {
  switch (size) {
    case 0:
      return proc.get_mmu()->load_uint8(addr);
    case 1:
      return proc.get_mmu()->load_uint16(addr);
    case 2:
      return proc.get_mmu()->load_uint32(addr);
    default:
      LOG(FATAL) << fmt::format("unknown load size {}", size);
  }
}

void VBridgeImpl::spike_mem_store(uint64_t addr, uint32_t size, uint64_t data) {
  switch (size) {
    case 0:
      proc.get_mmu()->store_uint8(addr, data);
      break;
    case 1:
      proc.get_mmu()->store_uint16(addr, data);
      break;
    case 2:
      proc.get_mmu()->store_uint32(addr, data);
      break;
    default:
      LOG(FATAL) << fmt::format("unknown store size {}", size);
  }
}

void VBridgeImpl::init_spike() {
  // reset spike CPU
  proc.reset();
  // TODO: remove this line, and use CSR write in the test code to enable this the VS field.
  proc.get_state()->sstatus->write(proc.get_state()->sstatus->read() | SSTATUS_VS);
  // load binary to reset_vector
  sim.load(bin, reset_vector);
}

void VBridgeImpl::init_simulator() {
  Verilated::traceEverOn(true);
  top.trace(&tfp, 99);
  tfp.open(wave.c_str());
  _cycles = timeout;
}

void VBridgeImpl::terminate_simulator() {
  tfp.close();
  top.final();
}

uint64_t VBridgeImpl::get_simulator_cycle() {
  return ctx.time();
}

std::optional<SpikeEvent> VBridgeImpl::spike_step() {
  auto state = proc.get_state();
  auto fetch = proc.get_mmu()->load_insn(state->pc);
  auto event = create_spike_event(fetch);
  auto &xr = proc.get_state()->XPR;
  if (event) {
    auto &se = event.value();
    // collect info to drive RTL
    // step spike
    state->pc = fetch.func(&proc, fetch.insn, state->pc);
    // todo: collect info for difftest
    se.log();
  } else {
    state->pc = fetch.func(&proc, fetch.insn, state->pc);
  }

  return event;
}

std::optional<SpikeEvent> VBridgeImpl::create_spike_event(insn_fetch_t fetch) {
  // create SpikeEvent
  uint32_t opcode = clip(fetch.insn.bits(), 0, 6);
  uint32_t width = clip(fetch.insn.bits(), 12, 14);
  bool load_type = opcode == 0b111;
  bool store_type = opcode == 0b100111;
  bool v_type = opcode == 0b1010111 && width != 0b111;
  if (load_type || store_type || v_type) {
    return SpikeEvent(proc, fetch);
  } else {
    return {};
  }
}

void VBridgeImpl::run() {

  init_spike();
  init_simulator();
  reset();

  // start loop
  while (true) {
    // spike =======> to_rtl_queue =======> rtl
    // when queue is not full
    while (to_rtl_queue.size() < to_rtl_queue_size) {
      try {
        if (auto spike_event = spike_step()) {
          auto se = spike_event.value();
          LOG(INFO) << fmt::format("enqueue Spike Event: {}", se.get_insn_disasm());
          //LOG(INFO) << fmt::format("issue: {}", se.get_issued());
          to_rtl_queue.push_front(se);
        }
      } catch (trap_t &trap) {
        LOG(FATAL) << fmt::format("spike trapped with {}", trap.name());
      }
    }
    LOG(INFO) << fmt::format("to_rtl_queue is full now, start to simulate.");

    // loop while there exists unissued insn in queue
    while (!to_rtl_queue.front().is_issued) {
      // in the RTL thread, for each RTL cycle, valid signals should be checked, generate events, let testbench be able
      // to check the correctness of RTL behavior, benchmark performance signals.
      SpikeEvent *se_to_issue = nullptr;
      for (auto iter = to_rtl_queue.rbegin(); iter != to_rtl_queue.rend(); iter++) {
        if (!iter->is_issued) {
          se_to_issue = &(*iter);
          break;
        }
      }
      LOG_ASSERT(se_to_issue) << fmt::format("all events in to_rtl_queue are is_issued");  // TODO: handle this

      se_to_issue->drive_rtl_req(top);
      se_to_issue->drive_rtl_csr(top);

      // drive instruction
      top.tlPort_0_a_ready = true;
      top.tlPort_1_a_ready = true;

      // Make sure any combinatorial logic depending upon inputs that may have changed before we called tick() has settled before the rising edge of the clock.
      top.clock = 1;
      top.eval();

      // Instruction is_issued, top.req_ready deps on top.req_bits_inst
      if (top.req_ready) {
        se_to_issue->is_issued = true;
        LOG(INFO) << fmt::format("Issue {:X} ({})", se_to_issue->pc, se_to_issue->get_insn_disasm());
      }

      receive_tl_req();

      // negedge
      top.clock = 0;
      top.eval();
      tfp.dump(2 * ctx.time());
      ctx.timeInc(1);
      // posedge
      // update registers
      top.clock = 1;
      top.eval();
      tfp.dump(2 * ctx.time() - 1);

      // TODO: record rf access
      {
        bool vrf_write_valid = false;
        for (int i = 0; i < 8; i++) {
          vrf_write_valid |= vpi_get_integer(fmt::format("TOP.V.laneVec_{}.vrf.write_valid", i).c_str());
        }
        if (vrf_write_valid) {
          // TODO: based on the RTL event, change se_to_issue rf field:
          //       1. based on the mask and write element, set corresponding element in vrf to written.
          LOG(INFO) << fmt::format("write to vrf");
        }
      }

      // TODO: record mem access
      {
        uint32_t lsuReqs[3];
        for (int i = 0; i < 3; i++) {
          lsuReqs[i] = vpi_get_integer(fmt::format("TOP.V.lsu.reqEnq_debug_{}", i).c_str());
        }
        for (auto iter = to_rtl_queue.rbegin(); iter != to_rtl_queue.rend(); iter++) {
          if (iter->is_issued && (iter->is_load || iter->is_store) && (iter->lsu_idx == 255)) {
            uint8_t index = 255;
            if (lsuReqs[0] == 1) {
              index = 0;
            } else if (lsuReqs[1] == 1) {
              index = 1;
            } else if (lsuReqs[2] == 1) {
              index = 2;
            } else {
              LOG(FATAL) << fmt::format("time: {}, load store is_issued but not no slot allocated.", ctx.time());
            }
            se_to_issue->lsu_idx = index;
            LOG(INFO) << fmt::format("slot {} is occupied by DSAM: {}", index, se_to_issue->get_insn_disasm());
            break;
          }
        }
      }

      // TODO: check when commit
      if (top.resp_valid) {
        LOG(INFO) << fmt::format("Commit {:X}", to_rtl_queue.back().pc);
        to_rtl_queue.pop_back();
      }

      if (get_simulator_cycle() >= timeout)
        throw TimeoutException();
    }
    LOG(INFO) << fmt::format("all insn in to_rtl_queue is issued, restarting spike");
  }
}

void VBridgeImpl::receive_tl_req() {
#define TL(i, name) (get_tl_##name(top, (i)))
  for (int tlIdx = 0; tlIdx < 2; tlIdx++) {
    if (!TL(tlIdx, a_valid)) continue;
    uint8_t opcode = TL(tlIdx, a_bits_opcode);
    uint32_t addr = TL(tlIdx, a_bits_address);
    uint32_t data = TL(tlIdx, a_bits_data);
    uint8_t size = TL(tlIdx, a_bits_size);
    uint8_t src = TL(tlIdx, a_bits_source);   // MSHR id, TODO: be returned in D channel
    uint32_t lsu_index = TL(tlIdx, a_bits_source) & 3;
    SpikeEvent *se;
    for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
      if (se_iter->lsu_idx == lsu_index) {
        se = &(*se_iter);
      }
    }
    LOG_ASSERT(se) << fmt::format("cannot find SpikeEvent with lsu_idx={}", lsu_index);
    switch (opcode) {
    case TlOpcode::Get:
      // TODO: handle get
    case TlOpcode::PutFullData:
      // TODO: handle put
    default:
      LOG(FATAL) << fmt::format("unknown tl opcode {}", opcode);
    }
  }
#undef TL
}
