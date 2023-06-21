#include <fmt/core.h>
#include <glog/logging.h>

#include <disasm.h>

#include <verilated.h>
#include <decode_macros.h>

#include "exceptions.h"
#include "glog_exception_safe.h"
#include "util.h"
#include "vbridge_impl.h"

/// convert TL style size to size_by_bytes
inline uint32_t decode_size(uint32_t encoded_size) {
  return 1 << encoded_size;
}

inline bool is_pow2(uint32_t n) {
  return n && !(n & (n - 1));
}

uint32_t expand_mask(uint8_t mask) {
  uint64_t x = mask & 0xF;
  x = (x | (x << 14)) & 0x00030003;
  x = (x | (x <<  7)) & 0x01010101;
  x = (x << 8) - x;
  return (uint32_t)x;
}

void VBridgeImpl::timeoutCheck() {
  getCoverage();
  if (get_t() > timeout + last_commit_time) {
    LOG(FATAL_S) << fmt::format("Simulation timeout, t={}, last_commit={}", get_t(), last_commit_time);
  }
}

void VBridgeImpl::dpiInitCosim() {
  google::InitGoogleLogging("emulator");

  ctx = Verilated::threadContextp();
  LOG(INFO) << fmt::format("[{}] dpiInitCosim", getCycle());
  proc.reset();
  // TODO: remove this line, and use CSR write in the test code to enable this the VS field.
  proc.get_state()->sstatus->write(proc.get_state()->sstatus->read() | SSTATUS_VS | SSTATUS_FS);
  auto load_result = sim.load_elf(bin);
  LOG(INFO) << fmt::format("Simulation Environment Initialized: bin={}, wave={}, timeout={}, entry={:08X}",
                           bin, wave, timeout, load_result.entry_addr);
  proc.get_state()->pc = load_result.entry_addr;
#if VM_TRACE
  dpiDumpWave();
#endif
}

/* cosim                          rtl
 * clock = 1
 * (prepare spike_event)
 *    <----- lsu idx -----------  (peekLsuEnq      posedge 1)
 *    <---- rf access ----------  (peekVrfWrite    posedge 1)
 *    <------- resp ------------  (peekResponse    posedge 1)
 *    <------- inst,csr --------  (peekInstruction posedge 1)
 *
 *    ------- inst ------------>  (peekIssue       posedge 2)
 *    ------- tl resp --------->  (pokeTL          posedge 2)
 *
 *    <------ tl req -----------  (peekTL          posedge 3)
 * clock = 0, eval
 *    <---- rf queue access ----  (peekWriteQueue  negedge 1)
 */

//==================
// posedge (1)
//==================

void VBridgeImpl::dpiPeekLsuEnq(const VLsuReqEnqPeek &lsu_req_enq) {
  VLOG(3) << fmt::format("[{}] dpiPeekLsuEnq", get_t());
  update_lsu_idx(lsu_req_enq);
}

void VBridgeImpl::dpiPeekVrfWrite(const VrfWritePeek &vrf_write) {
  VLOG(3) << fmt::format("[{}] dpiPeekVrfWrite", get_t());
  CHECK_S(0 <= vrf_write.lane_index && vrf_write.lane_index < config.lane_number);
  record_rf_accesses(vrf_write);
}

void VBridgeImpl::dpiPokeInst(const VInstrInterfacePoke &v_instr, const VCsrInterfacePoke &v_csr, const VRespInterface &v_resp) {
  VLOG(3) << fmt::format("[{}] dpiPokeInst", get_t());
  for (auto &se:to_rtl_queue) {
    VLOG(2) << fmt::format(" - se={}, issued={}, issue_idx={}", se.describe_insn(), se.is_issued, se.issue_idx);
  }

  if (v_resp.valid) {
    LOG(INFO) << fmt::format("[{}] prepare to commit", get_t());
    SpikeEvent &se = to_rtl_queue.back();
    se.record_rd_write(v_resp);
    se.check_is_ready_for_commit();
    LOG(INFO) << fmt::format("[{}] rtl commit insn ({})", get_t(), to_rtl_queue.back().describe_insn());
    last_commit_time = get_t();
    to_rtl_queue.pop_back();
  }

  while (true) {
    se_to_issue = find_se_to_issue();
    if ((se_to_issue->is_vfence_insn || se_to_issue->is_exit_insn) && to_rtl_queue.size() == 1) {
      if (se_to_issue->is_exit_insn) {
        LOG(INFO) << fmt::format("[{}] reaching exit instruction ({})", get_t(), se_to_issue->describe_insn());
        throw ReturnException();
      }

      to_rtl_queue.pop_back();
    } else {
      break;
    }
  }

  if (se_to_issue->is_vfence_insn || se_to_issue->is_exit_insn) {
    // it is ensured there are some other instruction not committed, thus se_to_issue should not be issued
    CHECK_S(to_rtl_queue.size() > 1);
    if (se_to_issue->is_exit_insn) {
      LOG(INFO) << fmt::format("[{}] exit waiting for fence", get_t());
    } else {
      LOG(INFO) << fmt::format("[{}] waiting for fence, no issuing new instruction", get_t());
    }
    *v_instr.valid = false;
  } else {
    LOG(INFO) << fmt::format("[{}] poke instruction ({}, rs1={:08X}, rs2={:08X})",
                             get_t(), se_to_issue->describe_insn(), se_to_issue->rs1_bits, se_to_issue->rs2_bits);
    se_to_issue->drive_rtl_req(v_instr);
  }
  se_to_issue->drive_rtl_csr(v_csr);
}

//==================
// posedge (2)
//==================

void VBridgeImpl::dpiPeekIssue(svBit ready, svBitVecVal issueIdx) {
  VLOG(3) << fmt::format("[{}] {}", get_t(), __func__);
  if (ready && !(se_to_issue->is_vfence_insn || se_to_issue->is_exit_insn)) {
    se_to_issue->is_issued = true;
    se_to_issue->issue_idx = issueIdx;
    LOG(INFO) << fmt::format("[{}] issue to rtl ({}, issue_idx={})", get_t(), se_to_issue->describe_insn(), issueIdx);
  }
}

void VBridgeImpl::dpiPokeTL(const VTlInterfacePoke &v_tl_resp) {
  VLOG(3) << fmt::format("[{}] dpiPokeTL", get_t());
  CHECK_S(0 <= v_tl_resp.channel_id && v_tl_resp.channel_id < config.tl_bank_number);
  return_tl_response(v_tl_resp);
}

//==================
// posedge (3)
//==================

void VBridgeImpl::dpiPeekTL(const VTlInterface &v_tl) {
  VLOG(3) << fmt::format("[{}] dpiPeekTL", get_t());
  CHECK_S(0 <= v_tl.channel_id && v_tl.channel_id < config.tl_bank_number);
  receive_tl_d_ready(v_tl);
  receive_tl_req(v_tl);
}

//==================
// negedge (1)
//==================

void VBridgeImpl::dpiPeekWriteQueue(const VLsuWriteQueuePeek &lsu_queue) {
  VLOG(3) << fmt::format("[{}] dpiPeekWriteQueue", get_t());
  CHECK_S(0 <= lsu_queue.mshr_index && lsu_queue.mshr_index < config.mshr_number);
  record_rf_queue_accesses(lsu_queue);

  se_to_issue = nullptr;   // clear se_to_issue, to avoid using the wrong one
}

VBridgeImpl::VBridgeImpl() :
    config(get_env_arg("COSIM_config")),
    varch(fmt::format("vlen:{},elen:{}", config.v_len, config.elen)),
    sim(1l << 32),
    isa("rv32gcv", "M"),
    cfg(/*default_initrd_bounds=*/ std::make_pair((reg_t) 0, (reg_t) 0),
        /*default_bootargs=*/ nullptr,
        /*default_isa=*/ DEFAULT_ISA,
        /*default_priv=*/ DEFAULT_PRIV,
        /*default_varch=*/ varch.data(),
        /*default_misaligned=*/ false,
        /*default_endianness*/ endianness_little,
        /*default_pmpregions=*/ 16,
        /*default_mem_layout=*/ std::vector<mem_cfg_t>(),
        /*default_hartids=*/ std::vector<size_t>(),
        /*default_real_time_clint=*/ false,
        /*default_trigger_count=*/ 4),
    proc(
        /*isa*/ &isa,
        /*cfg*/ &cfg,
        /*sim*/ &sim,
        /*id*/ 0,
        /*halt on reset*/ true,
        /*log_file_t*/ nullptr,
        /*sout*/ std::cerr),
    se_to_issue(nullptr),
    tl_banks(config.tl_bank_number),
    tl_current_req(config.tl_bank_number),
#ifdef COSIM_VERILATOR
    ctx(nullptr),
#endif
    vrf_shadow(std::make_unique<uint8_t[]>(config.v_len_in_bytes * config.vreg_number)) {

  DEFAULT_VARCH;
  auto &csrmap = proc.get_state()->csrmap;
  csrmap[CSR_MSIMEND] = std::make_shared<basic_csr_t>(&proc, CSR_MSIMEND, 0);
  proc.enable_log_commits();
}

uint64_t VBridgeImpl::get_t() {
  return getCycle();
}

std::optional<SpikeEvent> VBridgeImpl::spike_step() {
  auto state = proc.get_state();
  reg_t pc = state->pc;
  auto fetch = proc.get_mmu()->load_insn(pc);
//  VLOG(1) << fmt::format("pre-exec (pc={:08X})", state->pc);
  auto event = create_spike_event(fetch);

  clear_state(proc);
  if (event) {
    auto &se = event.value();
    LOG(INFO) << fmt::format("spike run vector insn ({}) (vl={}, sew={}, lmul={}, pc={:08X}, rs1={:08X}, rs2={:08X})",
                             se.describe_insn(), se.vl, (int) se.vsew, (int) se.vlmul, se.pc, se.rs1_bits, se.rs2_bits);
    se.pre_log_arch_changes();
    pc = fetch.func(&proc, fetch.insn, state->pc);
    se.log_arch_changes();
  } else {
    LOG(INFO) << fmt::format("spike run (pc={:08X}, bits={:08X}, disasm={})", pc, fetch.insn.bits(), proc.get_disassembler()->disassemble(fetch.insn));
    pc = fetch.func(&proc, fetch.insn, state->pc);
  }

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
        CHECK_S(false) << fmt::format("invalid pc (pc={:08X})", pc);
    }
  }

  return event;
}

std::optional<SpikeEvent> VBridgeImpl::create_spike_event(insn_fetch_t fetch) {
  // create SpikeEvent
  uint32_t opcode = clip(fetch.insn.bits(), 0, 6);
  uint32_t width = clip(fetch.insn.bits(), 12, 14);
  uint32_t rs1 = clip(fetch.insn.bits(), 15, 19);
  uint32_t csr = clip(fetch.insn.bits(), 20, 31);

  // for load/store instr, the opcode is shared with fp load/store. They can be only distinguished by func3 (i.e. width)
  // the func3 values for vector load/store are 000, 101, 110, 111, we can filter them out by ((width - 1) & 0b100)
  bool is_load_type  = opcode == 0b0000111 && ((width - 1) & 0b100);
  bool is_store_type = opcode == 0b0100111 && ((width - 1) & 0b100);
  bool is_v_type = opcode == 0b1010111;

  bool is_csr_type = opcode == 0b1110011 && (width & 0b011);
  bool is_csr_write = is_csr_type && ((width & 0b100) | rs1);
  bool is_vsetvl = opcode == 0b1010111 && width == 0b111;

  if (is_vsetvl) {
    return {};
  } else if (is_load_type || is_store_type || is_v_type || (is_csr_write && csr == CSR_MSIMEND)) {
    return SpikeEvent{proc, fetch, this};
  } else {
    return {};
  }
}

uint8_t VBridgeImpl::load(uint64_t address){
  return *sim.addr_to_mem(address);
}

void VBridgeImpl::receive_tl_req(const VTlInterface &tl) {
  int tlIdx = tl.channel_id;
  if (!tl.a_valid) return;

  uint8_t opcode = tl.a_bits_opcode;
  uint32_t addr = tl.a_bits_address;
  uint8_t mask = tl.a_bits_mask;
  uint8_t size = tl.a_bits_size;
  uint16_t src = tl.a_bits_source;   // MSHR id, TODO: be returned in D channel
  uint32_t lsu_index = tl.a_bits_source & 3;
  SpikeEvent *se;
  for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
    if (se_iter->lsu_idx == lsu_index) {
      se = &(*se_iter);
    }
  }
  CHECK_S(se) << fmt::format(": [{]] cannot find SpikeEvent with lsu_idx={}", get_t(), lsu_index);

  switch (opcode) {

  case TlOpcode::Get: {
    uint32_t decoded_size = decode_size(size);
    uint32_t actual_size = std::min(4, (int)decoded_size);
    CHECK_S((addr & ((1 << size)-1)) == 0) << fmt::format(": [{}] unaligned mem read of addr={:08X}, size={}byte", get_t(), addr, decoded_size);
    
    auto record = &tl_mem_load_counter[tlIdx];
    if (record->addr == addr) {
      CHECK_S(record->decoded_size == decoded_size) << fmt::format(": [{}] merged mem read has inconsistant size", get_t());
      CHECK_S(record->counter > 0) << fmt::format(": [{}] previous merged mem read has already completed", get_t());

      record->counter--;
    } else {
      CHECK_S(record->counter == 0) << fmt::format(": [{}] previous merged mem read has not yet completed", get_t());

      tl_mem_load_counter[tlIdx] = (TLMemCounterRecord){
        .counter = (decoded_size>>2) - 1,
        .decoded_size = decoded_size,
        .addr = addr,
      };
    }

    addr += decoded_size - ((record->counter+1) << 2);
    auto mem_read = se->mem_access_record.all_reads.find(addr);
    CHECK_S(mem_read != se->mem_access_record.all_reads.end())
      << fmt::format(": [{}] cannot find mem read of addr {:08X}", get_t(), addr);

    auto single_mem_read = mem_read->second.reads[mem_read->second.num_completed_reads++];
    uint32_t expected_size = single_mem_read.size_by_byte;
    uint32_t actual_data = 0;
    if ((expected_size <= actual_size) && (actual_size % expected_size == 0) && is_pow2(actual_size / expected_size)) {
      for (int i = 0; i < (actual_size / expected_size); i++) {
        actual_data |= single_mem_read.val << (i * expected_size * 8);
        if (i >= (actual_size / expected_size) - 1) break;
        addr += expected_size;
        mem_read = se->mem_access_record.all_reads.find(addr);
        CHECK_S(mem_read != se->mem_access_record.all_reads.end())
            << fmt::format(": [{}] cannot find mem read of addr={:08X}", get_t(), addr);
        single_mem_read = mem_read->second.reads[mem_read->second.num_completed_reads++];
      }
    } else {
      CHECK_S(false) << fmt::format(
          ": [{}] expect mem read of size {}, actual size {} (addr={:08X}, insn='{}')",
          get_t(), expected_size, actual_size, addr, se->describe_insn());
    }

    LOG(INFO) << fmt::format("[{}] <- receive rtl mem get req (addr={:08X}, size={}byte, src={:04X}), should return data {:04X}",
                             get_t(), addr, actual_size, src, actual_data);

    tl_banks[tlIdx].emplace(get_t(), TLReqRecord{
        actual_data, actual_size, src, TLReqRecord::opType::Get, get_mem_req_cycles()
    });
    break;
  }

  case TlOpcode::PutFullData: {
    uint32_t data = tl.a_bits_data;
    uint32_t decoded_size = decode_size(size);
    uint32_t actual_size = std::min(4, (int)decoded_size);
    CHECK_S((addr & ((1 << size)-1)) == 0) << fmt::format(": [{}] unaligned mem write of addr={:08X}, size={}byte", get_t(), addr, decoded_size);
    
    auto record = &tl_mem_store_counter[tlIdx];
    if (record->addr == addr) {
      CHECK_S(record->decoded_size == decoded_size) << fmt::format(": [{}] merged mem write has inconsistant size", get_t());
      CHECK_S(record->counter > 0) << fmt::format(": [{}] previous merged mem write has already completed", get_t());

      record->counter--;
    } else {
      CHECK_S(record->counter == 0) << fmt::format(": [{}] previous merged mem write has not yet completed", get_t());

      tl_mem_store_counter[tlIdx] = (TLMemCounterRecord){
        .counter = (decoded_size>>2) - 1,
        .decoded_size = decoded_size,
        .addr = addr,
      };
    }

    addr += decoded_size - ((record->counter+1) << 2);
    data = data & expand_mask(mask);
    LOG(INFO) << fmt::format("[{}] <- receive rtl mem put req (addr={:08X}, size={}byte, src={:04X}, data={}, mask={:04B}, counter={})",
                             get_t(), addr, decoded_size, src, data, mask, record->counter);

    if (mask == 0) {
      LOG(INFO) << fmt::format("[{}] mem put req masked out, skipping.. (addr={:08X}, size={}byte, src={:04X}, data={}, mask={:04B}, counter={})",
                             get_t(), addr, decoded_size, src, data, mask, record->counter);
      break;
    }

    auto mem_write = se->mem_access_record.all_writes.find(addr);

    CHECK_S(mem_write != se->mem_access_record.all_writes.end())
            << fmt::format(": [{}] cannot find mem write of addr={:08X}", get_t(), addr);

    auto single_mem_write = mem_write->second.writes[mem_write->second.num_completed_writes++];

    uint32_t expected_size = single_mem_write.size_by_byte;
    uint32_t actual_data = 0;
    if ((expected_size <= actual_size) && (actual_size % expected_size == 0) && is_pow2(actual_size / expected_size)) {
      for (int i = 0; i < (actual_size / expected_size); i++) {
        actual_data |= single_mem_write.val << (i * expected_size * 8);
        if (i >= (actual_size / expected_size) - 1) break;
        addr += expected_size;
        mem_write = se->mem_access_record.all_writes.find(addr);
        CHECK_S(mem_write != se->mem_access_record.all_writes.end())
            << fmt::format(": [{}] cannot find mem write of addr={:08X}", get_t(), addr);
        single_mem_write = mem_write->second.writes[mem_write->second.num_completed_writes++];
      }
    } else {
      CHECK_S(false) << fmt::format(
          ": [{}] expect mem write of size {}, actual size {} (addr={:08X}, insn='{}')",
          get_t(), expected_size, actual_size, addr, se->describe_insn());
    }

    CHECK_EQ_S(actual_data, data) << fmt::format(
        ": [{}] expect mem write of spike data {:08X}, rtl data {:08X} (addr={:08X}, insn='{}')",
        get_t(), actual_data, data, addr, se->describe_insn());

    tl_banks[tlIdx].emplace(get_t(), TLReqRecord{
        data, 1u << size, src, TLReqRecord::opType::PutFullData, get_mem_req_cycles()
    });

    break;
  }
  default: {
    LOG(FATAL_S) << fmt::format("unknown tl opcode {}", opcode);
  }
  }
}

void VBridgeImpl::receive_tl_d_ready(const VTlInterface &tl) {
  int tlIdx = tl.channel_id;

  if (tl.d_ready) {
    // check if there is a response waiting for RTL ready, clear if RTL is ready
    if (auto current_req_addr = tl_current_req[tlIdx]; current_req_addr.has_value()) {
      auto addr = current_req_addr.value();
      auto find = tl_banks[tlIdx].find(addr);
      CHECK_S(find != tl_banks[tlIdx].end()) << fmt::format(": [{}] cannot find current request with addr {:08X}", get_t(), addr);
      tl_current_req[tlIdx].reset();
      tl_banks[tlIdx].erase(find);
      LOG(INFO) << fmt::format("[{}] -> tl response reaches d_ready (channel={} addr={:08X})",
                               get_t(), tlIdx, addr);
    }
  }
}

void VBridgeImpl::return_tl_response(const VTlInterfacePoke &tl_poke) {
  // update remaining_cycles
  auto i = tl_poke.channel_id;
  for (auto &[addr, record]: tl_banks[i]) {
    if (record.remaining_cycles > 0) record.remaining_cycles--;
  }

  // find a finished request and return
  bool d_valid = false;
  *tl_poke.d_bits_source = 0;   // just for cleanness of the waveform, no effect
  for (auto &[addr, record]: tl_banks[i]) {

    if (record.remaining_cycles == 0) {
      LOG(INFO) << fmt::format("[{}] -> send tl response (channel={}, addr={:08X}, size={}byte, src={:04X}, data={:08X})",
                             get_t(), i, addr, record.size_by_byte, record.source, record.data);
      *tl_poke.d_bits_opcode = record.op == TLReqRecord::opType::Get ? TlOpcode::AccessAckData : TlOpcode::AccessAck;
      *tl_poke.d_bits_data = record.data;
      *tl_poke.d_bits_source = record.source;
      *tl_poke.d_bits_sink = 0;
      *tl_poke.d_corrupt = false;
      *tl_poke.d_bits_denied = false;
      d_valid = true;
      tl_current_req[i] = addr;
      break;
    }
  }
  *tl_poke.d_valid = d_valid;

  // welcome new requests all the time
  *tl_poke.a_ready = true;
}

void VBridgeImpl::update_lsu_idx(const VLsuReqEnqPeek &enq) {
  std::vector<uint32_t> lsuReqs(config.mshr_number);
  for (int i = 0; i < config.mshr_number; i++) {
    lsuReqs[i] = (enq.enq >> i) & 1;
  }
  for (auto se = to_rtl_queue.rbegin(); se != to_rtl_queue.rend(); se++) {
    if (se->is_issued && (se->is_load || se->is_store) && (se->lsu_idx == lsu_idx_default)) {
      uint8_t index = lsu_idx_default;
      for (int i = 0; i < config.mshr_number; i++) {
        if (lsuReqs[i] == 1) {
          index = i;
          break;
        }
      }
      if (index == lsu_idx_default) {
        LOG(INFO) << fmt::format("[{}] waiting for lsu request to fire.", get_t());
        break;
      }
      se->lsu_idx = index;
      LOG(INFO) << fmt::format("[{}] insn ({}) is allocated lsu_idx={}", get_t(), se->describe_insn(), index);
      break;
    }
  }
}

SpikeEvent *VBridgeImpl::find_se_to_issue() {
  SpikeEvent *unissued_se = nullptr;

  // search from tail, until finding an unissued se
  for (auto iter = to_rtl_queue.rbegin(); iter != to_rtl_queue.rend(); iter++) {
    if (!iter->is_issued) {
      unissued_se = &(*iter);
      break;
    }
  }

  // if no se is found, step spike to produce an se
  try {
    while (unissued_se == nullptr) {
      if (auto spike_event = spike_step()) {
        SpikeEvent &se = spike_event.value();
        to_rtl_queue.push_front(std::move(se));  // se cannot be copied since it has reference member
        unissued_se = &to_rtl_queue.front();
      }
    }
    return unissued_se;
  } catch (trap_t &trap) {
    LOG(FATAL_S) << fmt::format("spike trapped with {}", trap.name());
  }
}

void VBridgeImpl::record_rf_accesses(const VrfWritePeek &rf_write) {
  int valid = rf_write.valid;
  int lane_idx = rf_write.lane_index;
  if (valid) {
    uint32_t vd = rf_write.request_vd;
    uint32_t offset = rf_write.request_offset;
    uint32_t mask = rf_write.request_mask;
    uint32_t data = rf_write.request_data;
    uint32_t idx = rf_write.request_instIndex;
    SpikeEvent *se_vrf_write = nullptr;
    for (auto se = to_rtl_queue.rbegin(); se != to_rtl_queue.rend(); se++) {
      if (se->issue_idx == idx) {
        se_vrf_write = &(*se);
      }
    }
    if (se_vrf_write == nullptr) {
      LOG(WARNING) << fmt::format("[{}] rtl detect vrf write which cannot find se, maybe from committed load insn (idx={})", get_t(), idx);
    } else if (!se_vrf_write->is_load) {
      LOG(INFO) << fmt::format("[{}] rtl detect vrf write (lane={}, vd={}, offset={}, mask={:04b}, data={:08X}, insn idx={})",
                               get_t(), lane_idx, vd, offset, mask, data, idx);
      add_rtl_write(se_vrf_write, lane_idx, vd, offset, mask, data, idx);
    }
  }  // end if(valid)
}

void VBridgeImpl::record_rf_queue_accesses(const VLsuWriteQueuePeek &lsu_queue) {
  bool valid = lsu_queue.write_valid;
  if (valid) {
    uint32_t vd = lsu_queue.request_data_vd;
    uint32_t offset = lsu_queue.request_data_offset;
    uint32_t mask = lsu_queue.request_data_mask;
    uint32_t data = lsu_queue.request_data_data;
    uint32_t idx = lsu_queue.request_data_instIndex;
    uint32_t targetLane = lsu_queue.request_targetLane;
    int lane_idx = __builtin_ctz(targetLane);
    SpikeEvent *se_vrf_write = nullptr;
    for (auto se = to_rtl_queue.rbegin(); se != to_rtl_queue.rend(); se++) {
      if (se->issue_idx == idx) {
        se_vrf_write = &(*se);
      }
    }
    LOG(INFO) << fmt::format("[{}] rtl detect vrf queue write (lane={}, vd={}, offset={}, mask={:04b}, data={:08X}, insn idx={})",
                             get_t(), lane_idx, vd, offset, mask, data, idx);
    CHECK_S(se_vrf_write != nullptr) << fmt::format("cannot find se with issue_idx {}", idx);
    add_rtl_write(se_vrf_write, lane_idx, vd, offset, mask, data, idx);
  }
}

void VBridgeImpl::add_rtl_write(SpikeEvent *se, uint32_t lane_idx, uint32_t vd, uint32_t offset, uint32_t mask, uint32_t data, uint32_t idx) {
  uint32_t record_idx_base = vd * config.v_len_in_bytes + (lane_idx + config.lane_number * offset) * 4;
  auto &all_writes = se->vrf_access_record.all_writes;

  for (int j = 0; j < 32 / 8; j++) {  // 32bit / 1byte
    if ((mask >> j) & 1) {
      uint8_t written_byte = (data >> (8 * j)) & 255;
      auto record_iter = all_writes.find(record_idx_base + j);

      if (record_iter != all_writes.end()) { // if find a spike write record
        auto &record = record_iter->second;
        CHECK_EQ_S((int) record.byte, (int) written_byte) << fmt::format(  // convert to int to avoid stupid printing
              ": [{}] {}th byte incorrect ({:02X} != {:02X}) for vrf write (lane={}, vd={}, offset={}, mask={:04b}) [vrf_idx={}]",
              get_t(), j, record.byte, written_byte, lane_idx, vd, offset, mask, record_idx_base + j);
        record.executed = true;

      } else if (uint8_t original_byte = vrf_shadow[record_idx_base + j]; original_byte != written_byte) {
       CHECK_S(false) << fmt::format(": [{}] vrf writes {}th byte (lane={}, vd={}, offset={}, mask={:04b}, data={}, original_data={}), "
                                     "but not recorded by spike ({}) [{}]",
                                     get_t(), j, lane_idx, vd, offset, mask, written_byte,
                                     original_byte, se->describe_insn(), record_idx_base + j);
      } else {
        // no spike record and rtl written byte is identical as the byte before write, safe
      }

      vrf_shadow[record_idx_base + j] = written_byte;
    }  // end if mask
  }  // end for j
}

VBridgeImpl vbridge_impl_instance;
