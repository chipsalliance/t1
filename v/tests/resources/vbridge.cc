#include <fmt/core.h>
#include <glog/logging.h>

#include "VV.h"
#include "verilated_vcd_c.h"
#include "verilated.h"

#include "mmu.h"
#include "disasm.h"

#include "vbridge.h"
#include "tl.h"
#include "util.h"
#include "simple_sim.h"

constexpr int numTL = 2;

struct TLBank {
  uint32_t data;
  enum class opType {
    Nil, Get, PutFullData
  } op;
  int remainingCycles;

  TLBank() {
    op = opType::Nil;
  }

  void step() {
    if (remainingCycles > 0) remainingCycles--;
  }

  [[nodiscard]] bool done() const {
    return op != opType::Nil && remainingCycles == 0;
  }

  [[nodiscard]] bool ready() const {
    return op == opType::Nil;
  }

  void clear() {
    op = opType::Nil;
  }
};

class VBridgeImpl {
public:
  processor_t &proc;
  simple_sim &sim;

  VerilatedContext ctx;
  VV top;
  VerilatedVcdC tfp;

  explicit VBridgeImpl(processor_t &proc, simple_sim &sim) : proc(proc), sim(sim) {};

  void setup(int argc, char **argv);

  [[noreturn]] [[noreturn]] void loop();

private:
  void reset(int cycles);
  insn_fetch_t fetch_proc_insn();

  TLBank banks[numTL];

// the following macro helps us to access tl interface by dynamic index
#define TL_INTERFACE(type, name) \
type &get_tl_##name(int i) {      \
  switch (i) {                   \
  case 0: return top.tlPort_0_##name;                     \
  case 1: return top.tlPort_1_##name;                     \
  default: assert(false && "unknown tl port index");                                 \
  }\
}

#include "tl_interface.inc.h"
};

void VBridgeImpl::reset(int cycles) {
  top.reset = 1;
  for (int i = 0; i < cycles; i++) {
    top.clock = !top.clock;
    top.eval();
    ctx.timeInc(1);
    tfp.dump(ctx.time());
  }
  top.reset = 0;
}

void VBridgeImpl::setup(int argc, char **argv) {
  ctx.commandArgs(argc, argv);

  Verilated::traceEverOn(true);
  top.trace(&tfp, 99);
  tfp.open("/tmp/trace.log");

  size_t reset_vector = 0x1000;
  sim.load(argv[1], reset_vector);

  proc.get_state()->dcsr->halt = false;
  proc.get_state()->pc = reset_vector;
  proc.get_state()->sstatus->write(proc.get_state()->sstatus->read() | SSTATUS_VS);
  proc.VU.vill = false;
  proc.VU.vsew = 8;

  reset(4);
}

insn_fetch_t VBridgeImpl::fetch_proc_insn() {
  auto state = proc.get_state();
  mmu_t *_mmu = proc.get_mmu();
  auto ic_entry = _mmu->access_icache(state->pc);
  auto fetch = ic_entry->data;
  assert(ic_entry->tag == state->pc);
  return fetch;
}

[[noreturn]] void VBridgeImpl::loop() {
  // TODO: check state correctness

#define TL(i, name) (get_tl_##name((i)))
  auto state = proc.get_state();

  insn_t unsent_insn;
  enum {FREE, INSN_NOT_SENT, FULL_OF_INSN} v_state = FREE;

  while (true) {

    // run until vector insn
    if (v_state == FREE) {
      while (true) {
        auto f = fetch_proc_insn();
        auto as = proc.get_disassembler()->disassemble(f.insn);
        if (is_vector_instr(f.insn.bits())) {
          unsent_insn = f.insn;
          v_state = INSN_NOT_SENT;

          LOG(INFO) << fmt::format("[{}] new vector insn at pc={:X}, insn={:X} ({})", ctx.time(), state->pc, f.insn.bits(), as);
          break;
        } else {
          LOG(INFO) << fmt::format("[{}] new scalar insn at pc={:X}, insn={:X} ({})", ctx.time(), state->pc, f.insn.bits(), as);
          auto new_pc = f.func(&proc, f.insn, state->pc);
          state->pc = new_pc;
        }
      }
    }

    /* Spike                Vector
     *        -----rs----->
     *        ----insn---->
     * (return mem result)
     *        -----(D)---->
     *              (process vector)
     *              (create mem req)
     *        <----(A)-----
     */

    // tick clock
    top.clock = !top.clock;

    auto &xr = proc.get_state()->XPR;
    // send insn requests and reg values
    if (v_state == INSN_NOT_SENT) {
      top.req_bits_inst = (uint32_t) unsent_insn.bits();
      top.req_bits_src1Data = (uint32_t) xr[unsent_insn.rs1()];
      top.req_bits_src2Data = (uint32_t) xr[unsent_insn.rs2()];
      top.req_valid = true;
    }

    for (auto &t: banks) t.step();

    // send mem responses
    for (int i = 0; i < numTL; i++) {
      if (TL(i, d_ready) && banks[i].done()) {  // when vector accepts mem response
        TL(i, d_bits_opcode) =
            banks[i].op == TLBank::opType::Get
            ? TLOpCode::AccessAckData
            : TLOpCode::AccessAck;
        TL(i, d_valid) = true;
        TL(i, d_bits_data) = banks[i].data;
        banks[i].clear();
        LOG(INFO) << fmt::format("[{}] send vector TL response (bank={}, op={}, data={:X})", ctx.time(), i, (int)banks[i].op, banks[i].data);
      }
      // pull up ready signal
      if (banks[i].ready()) {
        TL(i, a_ready) = true;  // accept new mem requests
      }
    }

    // step vector unit and dump vcd
    top.eval();
    ctx.timeInc(1);
    tfp.dump(ctx.time());

    if (v_state == INSN_NOT_SENT && top.req_ready) {
      v_state = FULL_OF_INSN;
      LOG(INFO) << fmt::format("[{}] succeed to send insn to vector unit", ctx.time());
    }

    // receive mem requests
    constexpr uint8_t acceptedSize = 2;
    constexpr int memCycles = 100;
    for (int i = 0; i < numTL; i++) {
      if (TL(i, a_ready) && TL(i, a_valid)) {
        if (TL(i, a_bits_opcode) == TLOpCode::Get) {  // Get
          uint32_t addr = TL(i, a_bits_address);
          assert(TL(i, a_bits_size) == acceptedSize); // TODO: sure to write 32bits each time?
          banks[i].data = proc.get_mmu()->load_uint32(addr);
          banks[i].remainingCycles = memCycles;  // TODO: more sophisticated model
          LOG(INFO) << fmt::format("[{}] receive TL Get(addr={:X})", ctx.time(), addr);

        } else if (TL(i, a_bits_opcode) == TLOpCode::PutFullData) {  // PutFullData
          uint32_t data = TL(i, a_bits_data);
          uint32_t addr = TL(i, a_bits_address);
          assert(TL(i, a_bits_size) == acceptedSize); // TODO: sure to write 32bits each time?
          proc.get_mmu()->store_uint32(addr, data);
          banks[i].remainingCycles = memCycles;  // TODO: more sophisticated model
          LOG(INFO) << fmt::format("[{}] receive TL PutFullData(addr={:X}, data={:X})", ctx.time(), addr, data);

        } else {
          assert(false && "not supported tl opType");
        }
      }
    }

    if (top.resp_valid) {
      // TODO: check whether we should write rd
      xr.write(unsent_insn.rd(), top.resp_bits_data);
      LOG(INFO) << fmt::format("[{}] insn {:X} consumed", ctx.time(), unsent_insn.bits());
      v_state = FREE;  // TODO: now we process instructions one by one, to be optimized later
    }
  }
}

void VBridge::setup(int argc, char **argv) const {
  impl->setup(argc, argv);
}

VBridge::VBridge(processor_t &proc, simple_sim &sim) : impl(new VBridgeImpl(proc, sim)) {}

VBridge::~VBridge() {
  delete impl;
}

void VBridge::loop() const {
  impl->loop();
}
