#include <fmt/core.h>
#include <glog/logging.h>

#include "vbridgeimpl.h"
#include "verilated.h"
#include "disasm.h"
#include "vbridge.h"
#include "tl.h"
#include "util.h"

void VBridgeImpl::reset() {
  top.reset = 1;
  rtl_tick();
  rtl_tick();
  top.reset = 0;
}

void VBridgeImpl::rtl_tick() {
  top.clock = !top.clock;
  top.eval();
  tfp.dump(ctx.time());
  ctx.timeInc(1);

  top.clock = !top.clock;
  top.eval();
  tfp.dump(ctx.time());
  ctx.timeInc(1);
}

uint64_t VBridgeImpl::rtl_cycle() {
  return ctx.time() / 2;
}

void VBridgeImpl::setup(const std::string &bin, const std::string &wave, uint64_t reset_vector, uint64_t cycles) {
  Verilated::traceEverOn(true);
  top.trace(&tfp, 99);
  tfp.open(wave.c_str());
  sim.load(bin, reset_vector);
  _cycles = cycles;
  proc.get_state()->dcsr->halt = false;
  proc.get_state()->pc = reset_vector;
  proc.get_state()->sstatus->write(proc.get_state()->sstatus->read() | SSTATUS_VS);
  proc.VU.vill = false;
  proc.VU.vsew = 8;
  reset();
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

  while (unlikely(rtl_cycle() <= _cycles)) {
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

    auto &xr = proc.get_state()->XPR;
    // send insn requests and reg values
    if (v_state == INSN_NOT_SENT) {
      top.req_bits_inst = (uint32_t) unsent_insn.bits();
      top.req_bits_src1Data = (uint32_t) xr[unsent_insn.rs1()];
      top.req_bits_src2Data = (uint32_t) xr[unsent_insn.rs2()];
      top.csrInterface_vl = (uint16_t) proc.VU.vl->read();
      top.csrInterface_vStart = (uint16_t) proc.VU.vstart->read();
      top.csrInterface_vSew = (uint8_t) proc.VU.vsew;
      // TODO: not so sure here.
      // top.csrInterface_vlmul = (uint8_t) proc.VU.vflmul;
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

    // step vector unit and dump wave
    // tick clock
    rtl_tick();

    if (v_state == INSN_NOT_SENT && top.req_ready) {
      v_state = FULL_OF_INSN;
      auto f = fetch_proc_insn();
      state->pc = f.func(&proc, f.insn, state->pc);
      LOG(INFO) << fmt::format("[{}] succeed to send insn to vector unit", ctx.time());
    }

    // receive mem requests
    constexpr uint8_t acceptedSize = 2;
    constexpr int memCycles = 100;
    for (int i = 0; i < numTL; i++) {
      if (TL(i, a_ready) && TL(i, a_valid)) {
        if (TL(i, a_bits_opcode) == TLOpCode::Get) {  // Get
          uint32_t addr = TL(i, a_bits_address);
          banks[i].data = proc.get_mmu()->load_uint32(addr);
          banks[i].remainingCycles = memCycles;  // TODO: more sophisticated model
          LOG(INFO) << fmt::format("[{}] receive TL Get(addr={:X})", ctx.time(), addr);

        } else if (TL(i, a_bits_opcode) == TLOpCode::PutFullData) {  // PutFullData
          uint32_t data = TL(i, a_bits_data);
          uint32_t addr = TL(i, a_bits_address);
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
  exit(0);
}

VBridgeImpl::VBridgeImpl() :
    sim(1 << 30),
    isa("rv32gcv", "M"),
    proc(
        /*isa*/ &isa,
        /*varch*/ "vlen:128,elen:32",
        /*sim*/ &sim,
        /*id*/ 0,
        /*halt on reset*/ true,
        /*log_file_t*/ nullptr,
        /*sout*/ std::cerr) {}

VBridgeImpl::~VBridgeImpl() {
  tfp.close();
  top.final();
}

void VBridgeImpl::configure_simulator(int argc, char **argv) {
  ctx.commandArgs(argc, argv);
}
