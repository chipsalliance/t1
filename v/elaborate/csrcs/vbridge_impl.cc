#include <fmt/core.h>
#include <glog/logging.h>

#include "disasm.h"

#include "verilated.h"

#include "exceptions.h"
#include "vbridge_impl.h"
#include "util.h"
#include "rtl_event.h"
#include "vpi.h"
#include "tl_interface.h"

/// convert TL style size to size_by_bytes
inline uint32_t decode_size(uint32_t encoded_size) {
  return 1 << encoded_size;
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
        /*varch*/ fmt::format("vlen:{},elen:{}", consts::vlen_in_bits, consts::elen).c_str(),
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

uint64_t VBridgeImpl::get_t() {
  return ctx.time();
}

std::optional<SpikeEvent> VBridgeImpl::spike_step() {
  auto state = proc.get_state();
  auto fetch = proc.get_mmu()->load_insn(state->pc);
  auto event = create_spike_event(fetch);  // event not empty iff fetch is v inst
  auto &xr = proc.get_state()->XPR;
  if (event) {
    auto &se = event.value();
    LOG(INFO) << fmt::format("spike start exec insn ({}) (vl={}, sew={}, lmul={})",
                             se.describe_insn(), se.vl, (int) se.vsew, (int) se.vlmul);
    se.pre_log_arch_changes();
    state->pc = fetch.func(&proc, fetch.insn, state->pc);
    se.log_arch_changes();
  } else {
    state->pc = fetch.func(&proc, fetch.insn, state->pc);
  }

  return event;
}

std::optional<SpikeEvent> VBridgeImpl::create_spike_event(insn_fetch_t fetch) {
  // create SpikeEvent
  uint32_t opcode = clip(fetch.insn.bits(), 0, 6);
  uint32_t width = clip(fetch.insn.bits(), 12, 14);
  bool is_load_type  = opcode == 0b0000111;
  bool is_store_type = opcode == 0b0100111;
  bool v_type = opcode == 0b1010111 && width != 0b111;
  if (is_load_type || is_store_type || v_type) {
    return SpikeEvent{proc, fetch, this};
  } else {
    return {};
  }
}

uint8_t VBridgeImpl::load(uint64_t address){
  return *sim.addr_to_mem(address);
}

void VBridgeImpl::run() {

  init_spike();
  init_simulator();
  reset();

  // start loop
  while (true) {
    // spike =======> to_rtl_queue =======> rtl
    loop_until_se_queue_full();

    // loop while there exists unissued insn in queue
    while (!to_rtl_queue.front().is_issued) {
      // in the RTL thread, for each RTL cycle, valid signals should be checked, generate events, let testbench be able
      // to check the correctness of RTL behavior, benchmark performance signals.
      SpikeEvent *se_to_issue = find_se_to_issue();
      se_to_issue->drive_rtl_req(top);
      se_to_issue->drive_rtl_csr(top);

      return_tl_response();

      // Make sure any combinatorial logic depending upon inputs that may have changed before we called tick() has settled before the rising edge of the clock.
      top.clock = 1;
      top.eval();

      // Instruction is_issued, top.req_ready deps on top.req_bits_inst
      if (top.req_ready) {
        se_to_issue->is_issued = true;
        LOG(INFO) << fmt::format("[{}] issue to rtl ({})", get_t(), se_to_issue->describe_insn());
      }

      receive_tl_req();

      // negedge
      top.clock = 0;
      top.eval();
      tfp.dump(2 * ctx.time());
      ctx.timeInc(1);
      // posedge, update registers
      top.clock = 1;
      top.eval();
      tfp.dump(2 * ctx.time() - 1);

      record_rf_accesses();

      update_lsu_idx();

      if (top.resp_valid) {
        SpikeEvent &se = to_rtl_queue.back();
        se.record_rd_write(top);
        se.check_is_ready_for_commit();
        LOG(INFO) << fmt::format("[{}] rtl commit insn ({})", get_t(), to_rtl_queue.back().describe_insn());
        to_rtl_queue.pop_back();
      }

      if (get_t() >= timeout) {
        throw TimeoutException();
      }
    }
    LOG(INFO) << fmt::format("[{}] all insn in to_rtl_queue is issued, restarting spike", get_t());
  }
}

void VBridgeImpl::receive_tl_req() {
#define TL(i, name) (get_tl_##name(top, (i)))
  for (int tlIdx = 0; tlIdx < 2; tlIdx++) {
    if (!TL(tlIdx, a_valid)) continue;

    uint8_t opcode = TL(tlIdx, a_bits_opcode);
    uint32_t addr = TL(tlIdx, a_bits_address);
    uint8_t size = TL(tlIdx, a_bits_size);
    uint8_t src = TL(tlIdx, a_bits_source);   // MSHR id, TODO: be returned in D channel
    uint32_t lsu_index = TL(tlIdx, a_bits_source) & 3;
    SpikeEvent *se;
    for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
      if (se_iter->lsu_idx == lsu_index) {
        se = &(*se_iter);
      }
    }
    CHECK(se) << fmt::format(": [{]] cannot find SpikeEvent with lsu_idx={}", get_t(), lsu_index);

    switch (opcode) {

    case TlOpcode::Get: {
      LOG(INFO) << fmt::format("[{}] receive rtl mem get req (addr={}, size={}byte)", get_t(), addr, decode_size(size));
      auto mem_read = se->mem_access_record.all_reads.find(addr);
      CHECK(mem_read != se->mem_access_record.all_reads.end())
        << fmt::format(": [{}] cannot find mem read of addr {:08X}", get_t(), addr);
      CHECK_EQ(mem_read->second.size_by_byte, decode_size(size)) << fmt::format(
          ": [{}] expect mem read of size {}, actual size {} (addr={:08X}, {})",
          get_t(), mem_read->second.size_by_byte, 1 << decode_size(size), addr, se->describe_insn());

      uint64_t data = mem_read->second.val;
      tl_banks[tlIdx].emplace(std::make_pair(addr, TLReqRecord{
          data, 1u << size, src, TLReqRecord::opType::Get, get_mem_req_cycles()
      }));
      mem_read->second.executed = true;
      break;
    }

    case TlOpcode::PutFullData: {
      uint32_t data = TL(tlIdx, a_bits_data);
      LOG(INFO) << fmt::format("[{}] receive rtl mem put req (addr={:08X}, size={}byte, data={})",
                               addr, decode_size(size), data);
      auto mem_write = se->mem_access_record.all_writes.find(addr);

      CHECK(mem_write != se->mem_access_record.all_writes.end())
              << fmt::format(": [{}] cannot find mem write of addr={:08X}", get_t(), addr);
      CHECK_EQ(mem_write->second.size_by_byte, decode_size(size)) << fmt::format(
          ": [{}] expect mem write of size {}, actual size {} (addr={:08X}, insn='{}')",
          get_t(), mem_write->second.size_by_byte, 1 << decode_size(size), addr, se->describe_insn());
      CHECK_EQ(mem_write->second.val, data) << fmt::format(
          ": [{}] expect mem write of data {}, actual data {} (addr={:08X}, insn='{}')",
          get_t(), mem_write->second.size_by_byte, 1 << decode_size(size), addr, se->describe_insn());

      tl_banks[tlIdx].emplace(std::make_pair(addr, TLReqRecord{
          data, 1u << size, src, TLReqRecord::opType::PutFullData, get_mem_req_cycles()
      }));
      mem_write->second.executed = true;
      break;
    }
    default: {
      LOG(FATAL) << fmt::format("unknown tl opcode {}", opcode);
    }
    }
  }
#undef TL
}

void VBridgeImpl::return_tl_response() {
#define TL(i, name) (get_tl_##name(top, (i)))
  for (int i = 0; i < consts::numTL; i++) {
    // update remaining_cycles
    for (auto &[addr, record]: tl_banks[i]) {
      if (record.remaining_cycles > 0) record.remaining_cycles--;
    }

    // find a finished request and return
    bool d_valid = false;
    for (auto &[addr, record]: tl_banks[i]) {
      if (record.remaining_cycles == 0) {
        TL(i, d_bits_opcode) = record.op == TLReqRecord::opType::Get ? TlOpcode::AccessAckData : TlOpcode::AccessAck;
        TL(i, d_bits_data) = record.data;
        TL(i, d_bits_source) = record.source;
        d_valid = true;
        record.op = TLReqRecord::opType::Nil;
        break;
      }
    }
    TL(i, d_valid) = d_valid;

    // collect garbage
    erase_if(tl_banks[i], [](const auto &record) {
      return record.second.op == TLReqRecord::opType::Nil;
    });

    // welcome new requests all the time
    TL(i, a_ready) = true;
  }
#undef TL
}

void VBridgeImpl::update_lsu_idx() {
  uint32_t lsuReqs[consts::numMSHR];
  for (int i = 0; i < consts::numMSHR; i++) {
    lsuReqs[i] = vpi_get_integer(fmt::format("TOP.V.lsu.reqEnq_debug_{}", i).c_str());
  }
  for (auto se = to_rtl_queue.rbegin(); se != to_rtl_queue.rend(); se++) {
    if (se->is_issued && (se->is_load || se->is_store) && (se->lsu_idx == consts::lsuIdxDefault)) {
      uint8_t index = consts::lsuIdxDefault;
      for (int i = 0; i < consts::numMSHR; i++) {
        if (lsuReqs[i] == 1) {
          index = i;
          break;
        }
      }
      CHECK_NE(index, consts::lsuIdxDefault)
        << fmt::format(": [{}] load store issued but not no slot allocated.", get_t());
      se->lsu_idx = index;
      LOG(INFO) << fmt::format("[{}] insn ({}) is allocated lsu_idx={}", get_t(), se->describe_insn(), index);
      break;
    }
  }
}

void VBridgeImpl::loop_until_se_queue_full() {
  while (to_rtl_queue.size() < to_rtl_queue_size) {
    try {
      if (auto spike_event = spike_step()) {
        SpikeEvent &se = spike_event.value();
        to_rtl_queue.push_front(std::move(se));
      }
    } catch (trap_t &trap) {
      LOG(FATAL) << fmt::format("spike trapped with {}", trap.name());
    }
  }
  LOG(INFO) << fmt::format("to_rtl_queue is full now, start to simulate.");

}

SpikeEvent *VBridgeImpl::find_se_to_issue() {
  SpikeEvent *se_to_issue = nullptr;
  for (auto iter = to_rtl_queue.rbegin(); iter != to_rtl_queue.rend(); iter++) {
    if (!iter->is_issued) {
      se_to_issue = &(*iter);
      break;
    }
  }
  CHECK(se_to_issue) << fmt::format("[{}] all events in to_rtl_queue are is_issued", get_t());  // TODO: handle this
  return se_to_issue;
}

void VBridgeImpl::record_rf_accesses() {
  for (int i = 0; i < consts::numLanes; i++) {
    int valid = vpi_get_integer(fmt::format("TOP.V.laneVec_{}.vrf.write_valid", i).c_str());
    if (valid) {
      int vd = vpi_get_integer(fmt::format("TOP.V.laneVec_{}.vrf.write_bits_vd", i).c_str());
      int offset = vpi_get_integer(fmt::format("TOP.V.laneVec_{}.vrf.write_bits_offset", i).c_str());
      int mask = vpi_get_integer(fmt::format("TOP.V.laneVec_{}.vrf.write_bits_mask", i).c_str());
      int data = vpi_get_integer(fmt::format("TOP.V.laneVec_{}.vrf.write_bits_data", i).c_str());
      LOG(INFO) << fmt::format("[{}] rtl detect vrf write (lane={}, vd={}, offset={}, mask={:04b}, data={:08X})",
                               get_t(), i, vd, offset, mask, data);
    }
  }
}
