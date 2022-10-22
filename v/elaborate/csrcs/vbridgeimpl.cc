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
#include "verilated_vpi.h"

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
    se.reset_issue();
    se.reset_commit();
    // step spike
    state->pc = fetch.func(&proc, fetch.insn, state->pc);
    // todo: collect info for difftest
    se.log();
  } else {
    state->pc = fetch.func(&proc, fetch.insn, state->pc);
  }

  return event;
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



void VBridgeImpl::run() {
  init_spike();
  init_simulator();
  reset();
  // start loop
  while (true) {
    // when queue is not full
    while (to_rtl_queue.size() < to_rtl_queue_size) {
      try {
        if (auto spike_event = spike_step()) {
          auto se = spike_event.value();
          LOG(INFO) << fmt::format("enqueue Spike Event: {}", se.disam());
          //LOG(INFO) << fmt::format("issue: {}", se.get_issued());
          to_rtl_queue.push_front(se);
        }
      } catch (trap_t &trap) {
        LOG(FATAL) << fmt::format("spike trapped with {}", trap.name());
      }
    }
    LOG(INFO) << fmt::format("to_rtl_queue is full now, start to simulate.");
    // loop when the head of the list is unissued
    while (!to_rtl_queue.front().get_issued()) {
      // in the RTL thread, for each RTL cycle, valid signals should be checked, generate events, let testbench be able
      // to check the correctness of RTL behavior, benchmark performance signals.
      SpikeEvent *se = nullptr;
      for (auto iter = to_rtl_queue.rbegin(); iter != to_rtl_queue.rend(); iter++) {
        if (!iter->get_issued()) {
          se = &(*iter);
          break;
        }
      }

      // Stable Poke
      top.req_valid = true;
      top.req_bits_inst = se->instruction();
      top.csrInterface_vSew = se->vsew();
      top.csrInterface_vlmul = se->vlmul();
      top.csrInterface_vma = se->vma();
      top.csrInterface_vta = se->vta();
      top.csrInterface_vl = se->vl();
      top.csrInterface_vStart = se->vstart();
      top.csrInterface_vxrm = se->vxrm();
      top.csrInterface_ignoreException = false;
      top.storeBufferClear = true;

      // drive instruction
      top.tlPort_0_a_ready = true;
      top.tlPort_1_a_ready = true;

      // Make sure any combinatorial logic depending upon inputs that may have changed before we called tick() has settled before the rising edge of the clock.
      top.clock = 1;
      top.eval();

      // Instruction issued, top.req_ready deps on top.req_bits_inst
      if (top.req_ready) {
        se->issue();
        LOG(INFO) << fmt::format("Issue {:X}", se->pc());
      }

      // top.tlPort_0_a_valid deps on top.tlPort_0_a_ready
      // top.tlPort_1_a_valid deps on top.tlPort_1_a_ready
      bool p0_store = top.tlPort_0_a_valid && (top.tlPort_0_a_bits_opcode == 0);
      bool p1_store = top.tlPort_1_a_valid && (top.tlPort_1_a_bits_opcode == 0);
      bool p0_load = top.tlPort_0_a_valid && (top.tlPort_0_a_bits_opcode == 4);
      bool p1_load = top.tlPort_1_a_valid && (top.tlPort_1_a_bits_opcode == 4);

      if(p0_load){
        auto lsu_index = top.tlPort_0_a_bits_source & 3;
        LOG(INFO) << fmt::format("Find a port 0 load from slot {}", lsu_index);
        for (auto iter = to_rtl_queue.rbegin(); iter != to_rtl_queue.rend(); iter++) {
          if (iter->lsu_index() == lsu_index) {
            for(auto item: iter->log_mem_queue){
              uint64_t addr = std::get<0>(item);
              uint64_t value = mem_load(std::get<0>(item),std::get<2>(item)-1);
              uint8_t size =  std::get<2>(item);
              LOG(INFO) << fmt::format(" load addr, load back value, size = {:X}, {}, {}", addr,value,size);
            }
            break;
          }
        }
      }
      if(p0_store){
        LOG(INFO) << fmt::format("Find a port 0 store ins");
      }
      if(p1_load){
        LOG(INFO) << fmt::format("Find a port 1 load ins");
      }
      if(p1_store){
        LOG(INFO) << fmt::format("Find a port 1 store ins");
      }

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
      // peek
      // VPI Peek VRF Write Event
      {
        auto csr_write0 = vpi_handle_by_name((PLI_BYTE8 *) "TOP.V.laneVec_0.vrf.write_valid", NULL);
        s_vpi_value csr_write_valid_vpi_value0;
        csr_write_valid_vpi_value0.format = vpiIntVal;
        vpi_get_value(csr_write0, &csr_write_valid_vpi_value0);
        auto csr_write1 = vpi_handle_by_name((PLI_BYTE8 *) "TOP.V.laneVec_1.vrf.write_valid", NULL);
        s_vpi_value csr_write_valid_vpi_value1;
        csr_write_valid_vpi_value1.format = vpiIntVal;
        vpi_get_value(csr_write1, &csr_write_valid_vpi_value1);
        auto csr_write2 = vpi_handle_by_name((PLI_BYTE8 *) "TOP.V.laneVec_2.vrf.write_valid", NULL);
        s_vpi_value csr_write_valid_vpi_value2;
        csr_write_valid_vpi_value2.format = vpiIntVal;
        vpi_get_value(csr_write2, &csr_write_valid_vpi_value2);
        auto csr_write3 = vpi_handle_by_name((PLI_BYTE8 *) "TOP.V.laneVec_3.vrf.write_valid", NULL);
        s_vpi_value csr_write_valid_vpi_value3;
        csr_write_valid_vpi_value3.format = vpiIntVal;
        vpi_get_value(csr_write3, &csr_write_valid_vpi_value3);
        auto csr_write4 = vpi_handle_by_name((PLI_BYTE8 *) "TOP.V.laneVec_4.vrf.write_valid", NULL);
        s_vpi_value csr_write_valid_vpi_value4;
        csr_write_valid_vpi_value4.format = vpiIntVal;
        vpi_get_value(csr_write4, &csr_write_valid_vpi_value4);
        auto csr_write5 = vpi_handle_by_name((PLI_BYTE8 *) "TOP.V.laneVec_5.vrf.write_valid", NULL);
        s_vpi_value csr_write_valid_vpi_value5;
        csr_write_valid_vpi_value5.format = vpiIntVal;
        vpi_get_value(csr_write5, &csr_write_valid_vpi_value5);
        auto csr_write6 = vpi_handle_by_name((PLI_BYTE8 *) "TOP.V.laneVec_6.vrf.write_valid", NULL);
        s_vpi_value csr_write_valid_vpi_value6;
        csr_write_valid_vpi_value6.format = vpiIntVal;
        vpi_get_value(csr_write6, &csr_write_valid_vpi_value6);
        auto csr_write7 = vpi_handle_by_name((PLI_BYTE8 *) "TOP.V.laneVec_7.vrf.write_valid", NULL);
        s_vpi_value csr_write_valid_vpi_value7;
        csr_write_valid_vpi_value7.format = vpiIntVal;
        vpi_get_value(csr_write7, &csr_write_valid_vpi_value7);
        auto csr_write_valid =
          csr_write_valid_vpi_value0.value.integer
          || csr_write_valid_vpi_value1.value.integer
          || csr_write_valid_vpi_value2.value.integer
          || csr_write_valid_vpi_value3.value.integer
          || csr_write_valid_vpi_value4.value.integer
          || csr_write_valid_vpi_value5.value.integer
          || csr_write_valid_vpi_value6.value.integer
          || csr_write_valid_vpi_value7.value.integer;
        if (csr_write_valid) {
          // TODO: based on the RTL event, change se rf field:
          //       1. based on the mask and write element, set corresponding element in vrf to written.
          LOG(INFO) << fmt::format("write to vrf");
        }
      }

      // VPI Peek Memory Read Allocate Event, allocate at this cycle.
      {
        auto lsu_req_enq_slot0 = vpi_handle_by_name((PLI_BYTE8 *) "TOP.V.lsu.reqEnq_debug_0", NULL);
        s_vpi_value lsu_req_enq_slot0_value;
        lsu_req_enq_slot0_value.format = vpiIntVal;
        vpi_get_value(lsu_req_enq_slot0, &lsu_req_enq_slot0_value);
        auto lsu_req_enq_slot1 = vpi_handle_by_name((PLI_BYTE8 *) "TOP.V.lsu.reqEnq_debug_1", NULL);
        s_vpi_value lsu_req_enq_slot1_value;
        lsu_req_enq_slot1_value.format = vpiIntVal;
        vpi_get_value(lsu_req_enq_slot1, &lsu_req_enq_slot1_value);
        auto lsu_req_enq_slot2 = vpi_handle_by_name((PLI_BYTE8 *) "TOP.V.lsu.reqEnq_debug_2", NULL);
        s_vpi_value lsu_req_enq_slot2_value;
        lsu_req_enq_slot2_value.format = vpiIntVal;
        vpi_get_value(lsu_req_enq_slot2, &lsu_req_enq_slot2_value);

        for (auto iter = to_rtl_queue.rbegin(); iter != to_rtl_queue.rend(); iter++) {
          if (iter->get_issued() && (iter->is_load() || iter->is_store()) && (iter->lsu_index() == 255)) {
            uint8_t index = 255;
            if (lsu_req_enq_slot0_value.value.integer == 1) {
              index = 0;
            } else if (lsu_req_enq_slot1_value.value.integer == 1) {
              index = 1;
            } else if (lsu_req_enq_slot2_value.value.integer == 1) {
              index = 2;
            } else {
              LOG(FATAL) << fmt::format("time: {}, load store issued but not no slot allocated.", ctx.time());
            }
            se->set_lsu_index(index);
            LOG(INFO) << fmt::format("slot {} is occupied by DSAM: {}", index, se->disam());
            break;
          }
        }



      }

      // Commit Event
      if (top.resp_valid) {
        LOG(INFO) << fmt::format("Commit {:X}", to_rtl_queue.back().pc());
        to_rtl_queue.back().commit();
        to_rtl_queue.pop_back();
      }


      if (get_simulator_cycle() >= timeout)
        throw TimeoutException();
    }
    LOG(INFO) << fmt::format("to_rtl_queue is empty now.");
  }
}