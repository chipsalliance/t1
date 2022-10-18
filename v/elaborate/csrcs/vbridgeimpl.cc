#include <fmt/core.h>
#include <glog/logging.h>

#include "vbridgeimpl.h"
#include "verilated.h"
#include "disasm.h"
#include "vbridge.h"
#include "tl.h"
#include "util.h"
#include "exceptions.h"
#include "RTLEvent.h"

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
  this->bin = bin;
  this->wave = wave;
  this->reset_vector = reset_vector;
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

void VBridgeImpl::loop() {
  // TODO: check state correctness

#define TL(i, name) (get_tl_##name((i)))
  auto state = proc.get_state();

  insn_t unsent_insn;
  enum {FREE, INSN_NOT_SENT, FULL_OF_INSN} v_state = FREE;

  while (rtl_cycle() <= _cycles) {
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
      // pull down valid signal
      if (!banks[i].done()) {
        TL(i, d_valid) = false;
      }

      if (TL(i, d_ready) && banks[i].done()) {  // when vector accepts mem response
        TL(i, d_bits_opcode) =
            banks[i].op == TLBank::opType::Get
            ? TLOpCode::AccessAckData
            : TLOpCode::AccessAck;
        TL(i, d_valid) = true;
        TL(i, d_bits_data) = banks[i].data;
        TL(i, d_bits_sink) = banks[i].source;
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
    constexpr int memCycles = 1;
    for (int i = 0; i < numTL; i++) {
      if (TL(i, a_ready) && TL(i, a_valid)) {
        uint32_t data = TL(i, a_bits_data);
        uint32_t addr = TL(i, a_bits_address);
        uint32_t size = TL(i, a_bits_size);
        uint32_t source = TL(i, a_bits_source);
        if (TL(i, a_bits_opcode) == TLOpCode::Get) {  // Get
          banks[i].op = TLBank::opType::Get;
          banks[i].data = mem_load(addr, size);
          banks[i].remainingCycles = memCycles;  // TODO: more sophisticated model
          banks[i].source = source;
          LOG(INFO) << fmt::format("[{}] receive TL Get(addr={:X})", ctx.time(), addr);
        } else if (TL(i, a_bits_opcode) == TLOpCode::PutFullData) {  // PutFullData
          mem_store(addr, size, data);
          banks[i].op = TLBank::opType::PutFullData;
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

uint64_t VBridgeImpl::mem_load(uint64_t addr, uint32_t size) {
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

void VBridgeImpl::mem_store(uint64_t addr, uint32_t size, uint64_t data) {
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
  // TODO: I don't think we should trigger here, this should be managed by driver.
  reset();
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
  auto event = create_spike_event(proc, fetch);
  auto &xr = proc.get_state()->XPR;
  if (event) {
    auto &se = event.value();
    // collect info to drive RTL
    se.log_reset();
    se.assign_instruction(fetch.insn.bits());
    se.set_src1(xr[fetch.insn.rs1()]);
    se.set_src2(xr[fetch.insn.rs2()]);
    se.set_vlmul((uint8_t) proc.VU.vflmul);
    se.set_vsew((uint8_t) proc.VU.vsew);
    se.set_vta(proc.VU.vta);
    se.set_vma(proc.VU.vma);
    se.set_vl((uint16_t) proc.VU.vl->read());
    se.set_vstart((uint16_t) proc.VU.vstart->read());
    se.get_mask();
    // step spike
    state->pc = fetch.func(&proc, fetch.insn, state->pc);
    // todo: collect info for difftest

    se.log();
  } else {
    // LOG(INFO) << fmt::format("before vl print: {}, pc: {}, ins:{}, src1:{}, getvlen:{}", (uint16_t) proc.VU.vl->read(),state->pc,fetch.insn.bits(),xr[fetch.insn.rs1()],proc.VU.get_vlen());
    // LOG(INFO) << fmt::format("PC: {}, ASM: {:032b}, DISASM: {}", state->pc, fetch.insn.bits() , proc.get_disassembler()->disassemble(fetch.insn.bits()));
    // step spike
    state->pc = fetch.func(&proc, fetch.insn, state->pc);
    // LOG(INFO) << fmt::format("the vl print: {}", (uint16_t) proc.VU.vl->read());
  }

  return event;
}

// return solid RTLEvent only when instruction is issued or  is committed
std::optional<RTLEvent> VBridgeImpl::rtl_step() {
  // tick clock
  rtl_tick();
  if (get_simulator_cycle() >= timeout)
    throw TimeoutException();
  // pick signals
  return create_rtl_event();
}

std::optional<RTLEvent> VBridgeImpl::create_rtl_event() {
  top.resp_bits_data;
  top.resp_valid;
  top.req_ready;

  top.tlPort_0_a_bits_source;
  top.tlPort_0_a_bits_address;
  top.tlPort_0_a_bits_data;
  top.tlPort_0_a_bits_corrupt;
  top.tlPort_0_a_bits_mask;
  top.tlPort_0_a_bits_opcode;
  top.tlPort_0_a_bits_param;
  top.tlPort_0_a_bits_size;
  top.tlPort_0_a_valid;
  top.tlPort_0_d_ready;

  top.tlPort_1_a_bits_source;
  top.tlPort_1_a_bits_address;
  top.tlPort_1_a_bits_data;
  top.tlPort_1_a_bits_corrupt;
  top.tlPort_1_a_bits_mask;
  top.tlPort_1_a_bits_opcode;
  top.tlPort_1_a_bits_param;
  top.tlPort_1_a_bits_size;
  top.tlPort_1_a_valid;
  top.tlPort_1_d_ready;

  top.verilator_debug_laneVec_0_vrf___05Fdebug_bits_data;
  top.verilator_debug_laneVec_1_vrf___05Fdebug_bits_data;
  top.verilator_debug_laneVec_2_vrf___05Fdebug_bits_data;
  top.verilator_debug_laneVec_3_vrf___05Fdebug_bits_data;
  top.verilator_debug_laneVec_4_vrf___05Fdebug_bits_data;
  top.verilator_debug_laneVec_5_vrf___05Fdebug_bits_data;
  top.verilator_debug_laneVec_6_vrf___05Fdebug_bits_data;
  top.verilator_debug_laneVec_7_vrf___05Fdebug_bits_data;
  top.verilator_debug_laneVec_0_vrf___05Fdebug_bits_eew;
  top.verilator_debug_laneVec_0_vrf___05Fdebug_bits_groupIndex;
  top.verilator_debug_laneVec_0_vrf___05Fdebug_bits_instIndex;
  top.verilator_debug_laneVec_0_vrf___05Fdebug_bits_last;
  top.verilator_debug_laneVec_0_vrf___05Fdebug_bits_vd;
  top.verilator_debug_laneVec_0_vrf___05Fdebug_valid;
  top.verilator_debug_laneVec_1_vrf___05Fdebug_bits_eew;
  top.verilator_debug_laneVec_1_vrf___05Fdebug_bits_groupIndex;
  top.verilator_debug_laneVec_1_vrf___05Fdebug_bits_instIndex;
  top.verilator_debug_laneVec_1_vrf___05Fdebug_bits_last;
  top.verilator_debug_laneVec_1_vrf___05Fdebug_bits_vd;
  top.verilator_debug_laneVec_1_vrf___05Fdebug_valid;
  top.verilator_debug_laneVec_2_vrf___05Fdebug_bits_eew;
  top.verilator_debug_laneVec_2_vrf___05Fdebug_bits_groupIndex;
  top.verilator_debug_laneVec_2_vrf___05Fdebug_bits_instIndex;
  top.verilator_debug_laneVec_2_vrf___05Fdebug_bits_last;
  top.verilator_debug_laneVec_2_vrf___05Fdebug_bits_vd;
  top.verilator_debug_laneVec_2_vrf___05Fdebug_valid;
  top.verilator_debug_laneVec_3_vrf___05Fdebug_bits_eew;
  top.verilator_debug_laneVec_3_vrf___05Fdebug_bits_groupIndex;
  top.verilator_debug_laneVec_3_vrf___05Fdebug_bits_instIndex;
  top.verilator_debug_laneVec_3_vrf___05Fdebug_bits_last;
  top.verilator_debug_laneVec_3_vrf___05Fdebug_bits_vd;
  top.verilator_debug_laneVec_3_vrf___05Fdebug_valid;
  top.verilator_debug_laneVec_4_vrf___05Fdebug_bits_eew;
  top.verilator_debug_laneVec_4_vrf___05Fdebug_bits_groupIndex;
  top.verilator_debug_laneVec_4_vrf___05Fdebug_bits_instIndex;
  top.verilator_debug_laneVec_4_vrf___05Fdebug_bits_last;
  top.verilator_debug_laneVec_4_vrf___05Fdebug_bits_vd;
  top.verilator_debug_laneVec_4_vrf___05Fdebug_valid;
  top.verilator_debug_laneVec_5_vrf___05Fdebug_bits_eew;
  top.verilator_debug_laneVec_5_vrf___05Fdebug_bits_groupIndex;
  top.verilator_debug_laneVec_5_vrf___05Fdebug_bits_instIndex;
  top.verilator_debug_laneVec_5_vrf___05Fdebug_bits_last;
  top.verilator_debug_laneVec_5_vrf___05Fdebug_bits_vd;
  top.verilator_debug_laneVec_5_vrf___05Fdebug_valid;
  top.verilator_debug_laneVec_6_vrf___05Fdebug_bits_eew;
  top.verilator_debug_laneVec_6_vrf___05Fdebug_bits_groupIndex;
  top.verilator_debug_laneVec_6_vrf___05Fdebug_bits_instIndex;
  top.verilator_debug_laneVec_6_vrf___05Fdebug_bits_last;
  top.verilator_debug_laneVec_6_vrf___05Fdebug_bits_vd;
  top.verilator_debug_laneVec_6_vrf___05Fdebug_valid;
  top.verilator_debug_laneVec_7_vrf___05Fdebug_bits_eew;
  top.verilator_debug_laneVec_7_vrf___05Fdebug_bits_groupIndex;
  top.verilator_debug_laneVec_7_vrf___05Fdebug_bits_instIndex;
  top.verilator_debug_laneVec_7_vrf___05Fdebug_bits_last;
  top.verilator_debug_laneVec_7_vrf___05Fdebug_bits_vd;
  top.verilator_debug_laneVec_7_vrf___05Fdebug_valid;
  auto re = RTLEvent();
  re.request_ready(top.req_ready);
  re.commit_ready(top.resp_valid);
  //set load_valid
//  if (top.tlPort_0_a_valid ){//tlport enable
//    if(top.tlPort_0_a_bits_opcode == 4){
//      re.set_load_valid(true);
//      re.set_load_base_address(top.tlPort_0_a_bits_address);
//    }
//    else if(top.tlPort_0_a_bits_opcode == 0){
//      re.set_store_valid(top.tlPort_0_a_valid);
//      re.set_store_base_address(top.tlPort_0_a_bits_address);
//      re.set_store_data(top.tlPort_0_a_bits_data);
//    }
//  }
//
//  if (top.tlPort_1_a_valid ){//tlport enable
//    if(top.tlPort_1_a_bits_opcode == 4){
//      re.set_load_valid(true);
//      re.set_load_base_address(top.tlPort_1_a_bits_address);
//    }
//    else if(top.tlPort_1_a_bits_opcode == 0){
//      re.set_store_valid(top.tlPort_1_a_valid);
//      re.set_store_base_address(top.tlPort_1_a_bits_address);
//      re.set_store_data(top.tlPort_1_a_bits_data);
//    }
//  }


  if(re.request() || re.commit() ) {
    return re;
  }
  return {};
}

std::optional<SpikeEvent> VBridgeImpl::create_spike_event(processor_t &proc, insn_fetch_t fetch) {
  // create SpikeEvent
  uint32_t opcode = clip(fetch.insn.bits(), 0, 6);
  uint32_t width = clip(fetch.insn.bits(), 12, 14);
  auto load_type = opcode == 0b111;
  auto store_type = opcode == 0b100111;
  auto v_type = opcode == 0b1010111 && width != 0b111;
  if (load_type || store_type || v_type) {
    return SpikeEvent(proc);
  } else {
    return {};
  }
}

void VBridgeImpl::poke_instruction(SpikeEvent se) {
  top.req_valid = true;
  top.req_bits_inst = se.instruction();
}

void VBridgeImpl::poke_csr_control(SpikeEvent se) {
  top.csrInterface_vSew = se.vsew();
  top.csrInterface_vlmul = se.vlmul();
  top.csrInterface_vma = se.vma();
  top.csrInterface_vta = se.vta();
  top.csrInterface_vl = se.vl();
  top.csrInterface_vStart = se.vstart();
  top.csrInterface_vxrm = se.vxrm();
  // TODO control this.
  top.csrInterface_ignoreException = false;
  // give event, making this false for some cycles.
  top.storeBufferClear = true;
  // poke valid signals to false
  top.req_bits_inst = false;
  top.req_valid = false;
}

void VBridgeImpl::run() {
  init_spike();
  init_simulator();
  // start loop
  while (true) {
    // when queue is not full
    while (to_rtl_queue.size() < to_rtl_queue_size) {
      try {
        if(auto spike_event = spike_step()) {
          auto se = spike_event.value();
          LOG(INFO) << fmt::format("enqueue Spike Event: {}", se.disam());
          to_rtl_queue.push_front(se);
        }
      } catch (trap_t& trap) {
        LOG(FATAL) << fmt::format("spike trapped with {}", trap.name());
      }
    }
    LOG(INFO) << fmt::format("to_rtl_queue is full now, start to simulate.");
    // loop when the head of the list is unissued
    while (!to_rtl_queue.front().get_issued()) {
      // in the RTL thread, for each RTL cycle, valid signals should be checked, generate events, let testbench be able
      // to check the correctness of RTL behavior, benchmark performance signals.

      //
      if(auto rtl_event = rtl_step()) {
        RTLEvent re = *rtl_event;
        // TODO: if CSR is changed, we need to wait for vector flush.
        // drive RTL csr from se
        poke_csr_control(to_rtl_queue.back());
        // if this RTLEvent is commit, pop the se in back
        if (re.commit()) {
          LOG(INFO) << fmt::format("SpikeEvent {} committed.", to_rtl_queue.back().disam());
          to_rtl_queue.back().commit();
          to_rtl_queue.pop_back();
        }
        // if rtl is ready for Queue, poke instruction to RTL and set se as issued
        if (re.request()) {
           for (auto iter = to_rtl_queue.rbegin(); iter != to_rtl_queue.rend(); iter++) {
             if (!iter->get_issued()) {
               iter->issue();
               poke_instruction(*iter);
               break;
             }
           }
        }
//        if (re.load_valid) {
//          // TODO: based on the RTL event, change se load field:
//          //       1. based on the load address and srcid, send load data cycle by cycle to RTL with srcid and data
//          //       2. set corresponding field to ture to avoid multiple load request.
//          //       3. drive load
//          LOG(INFO) << fmt::format("load from memory.");
//        }
//        if (re.store_valid) {
//          // TODO: based on the RTL event, change se store field:
//          //       1. based on the load address and srcid, reply ready and corresponding srcid to master.
//          //       2. set corresponding field to ture to avoid multiple store request.
//          //       3. drive store
//          LOG(INFO) << fmt::format("store to memory");
//        }
//        if (re.vrf_write_valid) {
//          // TODO: based on the RTL event, change se rf field:
//          //       1. based on the mask and write element, set corresponding element in vrf to written.
//          LOG(INFO) << fmt::format("write to vrf");
//        }
        // TODO: maintain the state here.
      }
    }
    LOG(INFO) << fmt::format("to_rtl_queue is empty now.");
  }
}