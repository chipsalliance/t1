#include <fmt/core.h>
#include <fmt/ranges.h>

#include <disasm.h>

#include <decode_macros.h>
#include <verilated.h>
#include <filesystem>

#include "exceptions.h"
#include "spdlog-ext.h"
#include "util.h"
#include "vbridge_impl.h"

/// convert TL style size to size_by_bytes
inline uint32_t decode_size(uint32_t encoded_size) { return 1 << encoded_size; }

inline bool is_pow2(uint32_t n) { return n && !(n & (n - 1)); }

void VBridgeImpl::timeoutCheck() {
  getCoverage();
  if (get_t() > timeout + last_commit_time) {
    Log("VBridgeImplTimeoutCheck")
        .with("last_commit", last_commit_time)
        .fatal("Simulation timeout");
  }
}

void VBridgeImpl::dpiInitCosim() {
  ctx = Verilated::threadContextp();
  Log("DPIInitCosim")
      .info("Initializing simulation environment");

  proc.reset();
  // TODO: remove this line, and use CSR write in the test code to enable this
  // the VS field.
  proc.get_state()->sstatus->write(proc.get_state()->sstatus->read() |
                                   SSTATUS_VS | SSTATUS_FS);

  auto load_result = sim.load_elf(bin);
  Log("DPIInitCosim")
      .with("config", get_env_arg("COSIM_config"))
      .with("bin", bin)
      .with("wave", wave)
      .with("timeout", timeout)
      .with("entry", fmt::format("{:08x}", load_result.entry_addr))
      .info("Simulation environment initialized");

  proc.get_state()->pc = load_result.entry_addr;
#if VM_TRACE
  dpiDumpWave();
#endif
}

/* cosim                          rtl
 * clock = 1
 * (prepare spike_event)
 *    <---- lsu idx ------------  (peekLsuEnq      posedge 1) [update_lsu_idx]
 *    <---- rf access ----------  (peekVrfWrite    posedge 1) [record_rf_accesses]
 *    <---- resp ---------------  (pokeInst        posedge 1)
 *    ----- tl resp ----------->  (pokeTL          posedge 1) [return_tl_response]
 *
 *    ----- issueIdx ---------->  (peekIssue       posedge 2)
 *    <---- tl req -------------  (peekTL          posedge 2) [receive_tl_d_ready, receive_tl_req]
 * clock = 0, eval
 *    <---- rf queue access ----  (peekWriteQueue  negedge 1) [record_rf_queue_accesses]
 */

//==================
// posedge (1)
//==================

void VBridgeImpl::dpiPeekLsuEnq(const VLsuReqEnqPeek &lsu_req_enq) {
  Log("DPIPeekLSUEnq").trace();

  update_lsu_idx(lsu_req_enq);
}

void VBridgeImpl::dpiPeekVrfWrite(const VrfWritePeek &vrf_write) {
  Log("DPIPeekVRFWrite").trace();

  CHECK(0 <= vrf_write.lane_index && vrf_write.lane_index < config.lane_number,
        "vrf_write have unexpected land index");
  record_rf_accesses(vrf_write);
}

void VBridgeImpl::dpiPokeInst(const VInstrInterfacePoke &v_instr,
                              const VCsrInterfacePoke &v_csr,
                              const VRespInterface &v_resp) {
  Log("DPIPokeInst").trace();

  for (auto &se : to_rtl_queue) {
    Log("DPIPokeInst")
        .with("insn", se.jsonify_insn())
        .with("is_issued", se.is_issued)
        .with("issue_idx", se.issue_idx)
        .trace();
  }

  if (v_resp.valid) {
    Log("DPIPokeInst").info("prepare to commit");

    SpikeEvent &se = to_rtl_queue.back();
    se.record_rd_write(v_resp);
    se.check_is_ready_for_commit();

    Log("DPIPokeInst")
        .with("insn", to_rtl_queue.back().jsonify_insn())
        .info("rtl commit insn");

    last_commit_time = get_t();
    to_rtl_queue.pop_back();
  }

  while (true) {
    se_to_issue = find_se_to_issue();
    if ((se_to_issue->is_vfence_insn || se_to_issue->is_exit_insn) &&
        to_rtl_queue.size() == 1) {
      if (se_to_issue->is_exit_insn) {
        Log("DPIPokeInst")
            .with("insn", se_to_issue->jsonify_insn())
            .info("reaching exit insturction");
        throw ReturnException();
      }

      to_rtl_queue.pop_back();
    } else {
      break;
    }
  }

  if (se_to_issue->is_vfence_insn || se_to_issue->is_exit_insn) {
    // it is ensured there are some other instruction not committed, thus
    // se_to_issue should not be issued
    CHECK_GT(to_rtl_queue.size(), 1, "to_rtl_queue are smaller than expected");
    if (se_to_issue->is_exit_insn) {
      Log("DPIPokeInst").info("exit waiting for fence");
    } else {
      Log("DPIPokeInst")
          .info("waiting for fence, no issuing new instruction");
    }
    *v_instr.valid = false;
  } else {
    Log("DPIPokeInst")
        .with("inst", se_to_issue->jsonify_insn())
        .with("rs1", fmt::format("{:08x}", se_to_issue->rs1_bits))
        .with("rs2", fmt::format("{:08x}", se_to_issue->rs2_bits))
        .info("poke instuction");
    se_to_issue->drive_rtl_req(v_instr);
  }
  se_to_issue->drive_rtl_csr(v_csr);
}

void VBridgeImpl::dpiPokeTL(const VTlInterfacePoke &v_tl_resp) {
  Log("DPIPokeTL").trace();
  CHECK(0 <= v_tl_resp.channel_id &&
        v_tl_resp.channel_id < config.tl_bank_number,
        "invalid v_tl_resp channel id");
  return_tl_response(v_tl_resp);

#ifndef COSIM_NO_DRAMSIM
  dramsim_drive(v_tl_resp.channel_id);
#endif
}

//==================
// posedge (2)
//==================

void VBridgeImpl::dpiPeekIssue(svBit ready, svBitVecVal issueIdx) {
  Log("DPIPeekIssue").with("func", __func__).trace();
  if (ready && !(se_to_issue->is_vfence_insn || se_to_issue->is_exit_insn)) {
    se_to_issue->is_issued = true;
    se_to_issue->issue_idx = issueIdx;
    Log("DPIPeekIssue")
        .with("insn", se_to_issue->jsonify_insn())
        .with("issue_idx", issueIdx)
        .info("issue to rtl");
  }
}

void VBridgeImpl::dpiPeekTL(const VTlInterface &v_tl) {
  Log("DPIPeekTL").trace();
  CHECK(0 <= v_tl.channel_id && v_tl.channel_id < config.tl_bank_number,
        "invalid v_tl channel id");
  receive_tl_d_ready(v_tl);
  receive_tl_req(v_tl);
}

//==================
// negedge (1)
//==================

void VBridgeImpl::dpiPeekWriteQueue(const VLsuWriteQueuePeek &lsu_queue) {
  Log("DPIPeekWriteQueue").trace();
  CHECK(0 <= lsu_queue.mshr_index && lsu_queue.mshr_index < config.lane_number,
        "invalid lsu_queue mshr index");
  record_rf_queue_accesses(lsu_queue);

  se_to_issue = nullptr; // clear se_to_issue, to avoid using the wrong one
}

//==================
// end of dpi interfaces
//==================

VBridgeImpl::VBridgeImpl()
    : config(get_env_arg("COSIM_config")),
      varch(fmt::format("vlen:{},elen:{}", config.v_len, config.elen)),
      sim(1l << 32), isa("rv32gcv", "M"),
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
          /*sout*/ std::cerr),
      se_to_issue(nullptr), tl_req_record_of_bank(config.tl_bank_number),
      tl_req_waiting_ready(config.tl_bank_number),
      tl_req_ongoing_burst(config.tl_bank_number),

#ifdef COSIM_VERILATOR
      ctx(nullptr),
#endif
      vrf_shadow(std::make_unique<uint8_t[]>(config.v_len_in_bytes *
                                             config.vreg_number)) {

  DEFAULT_VARCH;
  auto &csrmap = proc.get_state()->csrmap;
  csrmap[CSR_MSIMEND] = std::make_shared<basic_csr_t>(&proc, CSR_MSIMEND, 0);
  proc.enable_log_commits();

#ifndef COSIM_NO_DRAMSIM
  char *primary_tck_str = get_env_arg("COSIM_tck");
  tck = std::stod(primary_tck_str);

  char *dramsim_result_parent = get_env_arg("COSIM_dramsim3_result");
  char *dramsim_config = get_env_arg("COSIM_dramsim3_config");
  for(int i = 0; i < config.tl_bank_number; ++i) {
    std::string result_dir = std::string(dramsim_result_parent) + "/channel." + std::to_string(i);
    std::filesystem::create_directories(result_dir);
    auto completion = [i, this](uint64_t address) {
      this->dramsim_resolve(i, address);
    };

    drams.emplace_back(dramsim3::MemorySystem(dramsim_config, result_dir.c_str(), completion, completion), 0);
    // std::cout<<"Relative tck ratio on channel "<<i<<" = "<<tck / drams[i].first.GetTCK()<<std::endl;
  }
#endif
}

uint64_t VBridgeImpl::get_t() { return getCycle(); }

std::optional<SpikeEvent> VBridgeImpl::spike_step() {
  auto state = proc.get_state();
  reg_t pc = state->pc;
  auto fetch = proc.get_mmu()->load_insn(pc);
  //  VLOG(1) << fmt::format("pre-exec (pc={:08X})", state->pc);
  auto event = create_spike_event(fetch);

  clear_state(proc);
  if (event) {
    auto &se = event.value();
    Log("SpikeStep")
        .with("insn", se.jsonify_insn())
        .with("vl", se.vl)
        .with("sew", (int)se.vsew)
        .with("lmul", (int)se.vlmul)
        .with("pc", se.pc)
        .with("rs1", fmt::format("{:08x}", se.rs1_bits))
        .with("rs2", fmt::format("{:08x}", se.rs2_bits))
        .info("spike run vector insn");
    se.pre_log_arch_changes();
    pc = fetch.func(&proc, fetch.insn, state->pc);
    se.log_arch_changes();
  } else {
    Log("SpikeStep")
        .with("pc", fmt::format("{:08x}", pc))
        .with("bits", fmt::format("{:08x}", fetch.insn.bits()))
        .with("disasm", proc.get_disassembler()->disassemble(fetch.insn))
        .trace();
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
      Log("SpikeStep")
          .with("pc", fmt::format("{:08x}", pc))
          .fatal("invalid pc");
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

  // for load/store instr, the opcode is shared with fp load/store. They can be
  // only distinguished by func3 (i.e. width) the func3 values for vector
  // load/store are 000, 101, 110, 111, we can filter them out by ((width - 1) &
  // 0b100)
  bool is_load_type = opcode == 0b0000111 && ((width - 1) & 0b100);
  bool is_store_type = opcode == 0b0100111 && ((width - 1) & 0b100);
  bool is_v_type = opcode == 0b1010111;

  bool is_csr_type = opcode == 0b1110011 && (width & 0b011);
  bool is_csr_write = is_csr_type && ((width & 0b100) | rs1);
  bool is_vsetvl = opcode == 0b1010111 && width == 0b111;

  if (is_vsetvl) {
    return {};
  } else if (is_load_type || is_store_type || is_v_type ||
             (is_csr_write && csr == CSR_MSIMEND)) {
    return SpikeEvent{proc, fetch, this};
  } else {
    return {};
  }
}

uint8_t VBridgeImpl::load(uint64_t address) {
  return *sim.addr_to_mem(address);
}

void VBridgeImpl::receive_tl_req(const VTlInterface &tl) {
  int tlIdx = tl.channel_id;
  if (!tl.a_valid)
    return;

  uint8_t opcode = tl.a_bits_opcode;
  uint32_t base_addr = tl.a_bits_address;

  size_t size_encoded = tl.a_bits_size;
  size_t size = decode_size(size_encoded);
  uint16_t src = tl.a_bits_source; // MSHR id, TODO: be returned in D channel
  uint32_t lsu_index = tl.a_bits_source & 3;
  const uint32_t *mask = tl.a_bits_mask;
  SpikeEvent *se;
  for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend();
       se_iter++) {
    if (se_iter->lsu_idx == lsu_index) {
      se = &(*se_iter);
    }
  }
  CHECK(se, fmt::format("cannot find SpikeEvent with lsu_idx={}",
                        lsu_index));
  CHECK_EQ((base_addr & (size - 1)), 0,
           fmt::format("unaligned access (addr={:08X}, size={})",
                       base_addr, size));

  switch (opcode) {

  case TlOpcode::Get: {
    std::vector<uint8_t> actual_data(size);
    for (size_t offset = 0; offset < size; offset++) {
      uint32_t addr = base_addr + offset;
      auto mem_read = se->mem_access_record.all_reads.find(addr);
      if (mem_read != se->mem_access_record.all_reads.end()) {
        auto single_mem_read =
            mem_read->second.reads[mem_read->second.num_completed_reads++];
        actual_data[offset] = single_mem_read.val;
      } else {
        // TODO: check if the cache line should be accessed
        Log("ReceiveTLReq")
            .with("addr", fmt::format("{:08x}", addr))
            .trace();
        actual_data[offset] = 0xDE; // falsy data
      }
    }

    Log("ReceiveTLReq")
        .with("channel", tlIdx)
        .with("bass_addr", fmt::format("{:08x}", base_addr))
        .with("size_by_byte", size)
        .with("mask", fmt::format("{:b}", *mask))
        .with("src", fmt::format("{:04X}", src))
        .with("return_data", fmt::format("{:02X}", fmt::join(actual_data, " ")))
        .info("<- receive rtl mem get req");

    // TODO: ifndef COSIM_NO_DRAMSIM
    tl_req_record_of_bank[tlIdx].emplace(
        get_t(), TLReqRecord{get_t(), actual_data, size, base_addr, src,
                             TLReqRecord::opType::Get, dramsim_burst_size(tlIdx)});
    break;
  }

  case TlOpcode::PutFullData: {
    TLReqRecord *cur_record = nullptr;
    // determine if it is a beat of ongoing burst
    if (tl_req_ongoing_burst[tlIdx].has_value()) {
      auto find = tl_req_record_of_bank[tlIdx].find(
          tl_req_ongoing_burst[tlIdx].value());
      if (find != tl_req_record_of_bank[tlIdx].end()) {
        auto &record = find->second;
        CHECK_LT(record.bytes_received, record.size_by_byte, "invalid record");
        if (record.bytes_received < record.size_by_byte) {
          CHECK_EQ(record.addr, base_addr, "inconsistent burst addr");
          CHECK_EQ(record.size_by_byte, size, "inconsistent burst size");
          Log("ReceiveTLReq")
              .with("channel", tlIdx)
              .with("base_addr", fmt::format("{:08X}", base_addr))
              .with("offset", record.bytes_received)
              .info("continue burst");
          cur_record = &record;
        }
      }
    }

    // else create a new record
    if (cur_record == nullptr) {
      auto record = tl_req_record_of_bank[tlIdx].emplace(
          get_t(),
          TLReqRecord{get_t(), std::vector<uint8_t>(size), size, base_addr, src,
                      TLReqRecord::opType::PutFullData, dramsim_burst_size(tlIdx)});
      cur_record = &record->second;
    }

    std::vector<uint8_t> data(size);
    size_t actual_beat_size = std::min(
        size, config.datapath_width_in_bytes); // since tl require alignment
    size_t data_begin_pos = cur_record->bytes_received;

    // receive put data
    for (size_t offset = 0; offset < actual_beat_size; offset++) {
      data[data_begin_pos + offset] = n_th_byte(tl.a_bits_data, offset);
    }
    Log("RTLMemPutReq")
        .with("channel", tlIdx)
        .with("addr", fmt::format("{:08X}", base_addr))
        .with("size_by_byte", size)
        .with("src", fmt::format("{:04X}", src))
        .with("data",
              fmt::format("{:02X}",
                          fmt::join(data.begin() + (long)data_begin_pos,
                                    data.begin() + (long)(data_begin_pos +
                                                          actual_beat_size),
                                    " ")))
        .with("offset", data_begin_pos)
        .with("mask", fmt::format("{:04b}", *mask))
        .info("<- receive rtl mem put req");

    // compare with spike event record
    for (size_t offset = 0; offset < actual_beat_size; offset++) {
      size_t byte_lane_idx =
          (base_addr & (config.datapath_width_in_bytes - 1)) + offset;
      if (n_th_bit(mask, byte_lane_idx)) {
        uint32_t byte_addr = base_addr + cur_record->bytes_received + offset;
        uint8_t tl_data_byte = n_th_byte(tl.a_bits_data, byte_lane_idx);
        auto mem_write = se->mem_access_record.all_writes.find(byte_addr);
        CHECK_NE(mem_write, se->mem_access_record.all_writes.end(),
                 fmt::format("cannot find mem write of byte_addr {:08X}",
                             byte_addr));
        //        for (auto &w : mem_write->second.writes) {
        //          LOG(INFO) << fmt::format("write addr={:08X}, byte={:02X}",
        //          byte_addr, w.val);
        //        }
        CHECK_LT(mem_write->second.num_completed_writes,
                 mem_write->second.writes.size(),
                 "written size should be smaller than completed writes");
        auto single_mem_write = mem_write->second.writes.at(
            mem_write->second.num_completed_writes++);
        CHECK_EQ(single_mem_write.val, tl_data_byte,
                 fmt::format("expect mem write of byte {:02X}, actual "
                             "byte {:02X} (channel={}, byte_addr={:08X}, {})",
                             single_mem_write.val, tl_data_byte, tlIdx,
                             byte_addr, se->describe_insn()));
      }
    }

    cur_record->bytes_received += actual_beat_size;

    // update tl_req_ongoing_burst
    if (cur_record->bytes_received < size) {
      tl_req_ongoing_burst[tlIdx] = cur_record->t;
    } else {
      tl_req_ongoing_burst[tlIdx].reset();
    }

    break;
  }
  default: {
    FATAL(fmt::format("unknown tl opcode {}", opcode));
  }
  }
}

void VBridgeImpl::receive_tl_d_ready(const VTlInterface &tl) {
  int tlIdx = tl.channel_id;

  if (tl.d_ready) {
    // check if there is a response waiting for RTL ready, clear if RTL is ready
    if (auto current_req_addr = tl_req_waiting_ready[tlIdx];
        current_req_addr.has_value()) {
      auto addr = current_req_addr.value();
      auto find = tl_req_record_of_bank[tlIdx].find(addr);
      CHECK_NE(
          find, tl_req_record_of_bank[tlIdx].end(),
          fmt::format("cannot find current request with addr {:08X}",
                      addr));
      auto &req_record = find->second;

      req_record.commit_tl_respones(config.datapath_width_in_bytes);
      if(req_record.done_return()) {
        Log("ReceiveTlDReady")
          .with("channel", tlIdx)
          .with("addr", fmt::format("{:08X}", addr))
          .info(fmt::format("-> tl response for {} reaches d_ready", req_record.op == TLReqRecord::opType::Get ? "Get" : "PutFullData"));
      }
      tl_req_waiting_ready[tlIdx].reset();

      // TODO(Meow): add this check back
      // FATAL(fmt::format("unknown opcode {}", (int) req_record.op))
    }
  }
}

void VBridgeImpl::return_tl_response(const VTlInterfacePoke &tl_poke) {
  // update remaining_cycles
  auto i = tl_poke.channel_id;
  // find a finished request and return
  bool d_valid = false;
  *tl_poke.d_bits_source = 0; // just for cleanness of the waveform, no effect

  // Right now, we only resolves the request at the head of the queue.

  // Pop all fully resolved requests
  while (!tl_req_record_of_bank[i].empty() &&
         tl_req_record_of_bank[i].begin()->second.fully_done())
    tl_req_record_of_bank[i].erase(tl_req_record_of_bank[i].begin());

  // Find first response that haven't finish returning

  auto next_return = tl_req_record_of_bank[i].begin();
  while(next_return != tl_req_record_of_bank[i].end() && next_return->second.done_return()) ++next_return;

  if(next_return != tl_req_record_of_bank[i].end()) {
    auto returned_resp = next_return->second.issue_tl_response(config.datapath_width_in_bytes);
    d_valid = returned_resp.operator bool();
    if(d_valid) {
      auto &record = next_return->second;
      auto [offset, len] = *returned_resp;
      Log("ReturnTlResponse")
          .with("channel", i)
          .with("addr", fmt::format("{:08x}", record.addr))
          .with("size_by_byte", record.size_by_byte)
          .with("src", fmt::format("{:04X}", record.source))
          .with("data",
                fmt::format(
                    "{}",
                    fmt::join(record.data.begin() + (long) offset,
                              record.data.begin() +
                                  (long)(offset + len),
                              " ")))
          .with("offset", offset)
          .info("-> send tl response");

      *tl_poke.d_bits_opcode = record.op == TLReqRecord::opType::Get
                                   ? TlOpcode::AccessAckData
                                   : TlOpcode::AccessAck;

      for (size_t ioffset = 0; ioffset < len; ioffset++) {
        // for GET request not aligned to data bus, put it to a correct byte
        // lane
        size_t byte_lane_idx =
            (record.addr & (config.datapath_width_in_bytes - 1)) + ioffset;
        ((uint8_t *)tl_poke.d_bits_data)[byte_lane_idx] =
            record.data[offset + ioffset];
      }

      *tl_poke.d_bits_source = record.source;
      *tl_poke.d_bits_sink = 0;
      *tl_poke.d_corrupt = false;
      *tl_poke.d_bits_denied = false;
    }
  }

  if (d_valid)
    tl_req_waiting_ready[i] = next_return->first;

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
    if (se->is_issued && (se->is_load || se->is_store) &&
        (se->lsu_idx == lsu_idx_default)) {
      uint8_t index = lsu_idx_default;
      for (int i = 0; i < config.mshr_number; i++) {
        if (lsuReqs[i] == 1) {
          index = i;
          break;
        }
      }
      if (index == lsu_idx_default) {
        Log("UpdateLSUIdx")
            .info("waiting for lsu request to fire");
        break;
      }
      se->lsu_idx = index;
      Log("UpdateLSUIdx")
          .with("insn", se->jsonify_insn())
          .with("lsu_idx", index)
          .info("Instruction is allocated");
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
        to_rtl_queue.push_front(
            std::move(se)); // se cannot be copied since it has reference member
        unissued_se = &to_rtl_queue.front();
      }
    }
    return unissued_se;
  } catch (trap_t &trap) {
    FATAL(fmt::format("spike trapped with {}", trap.name()));
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
      Log("RecordRFAccess")
          .with("index", idx)
          .warn("rtl detect vrf write which cannot find se, maybe from "
                "committed load insn");
    } else if (!se_vrf_write->is_load) {
      Log("RecordRFAccess")
          .with("lane", lane_idx)
          .with("vd", vd)
          .with("offset", offset)
          .with("mask", fmt::format("{:04b}", mask))
          .with("data", fmt::format("{:08X}", data))
          .with("instruction_index", idx)
          .info("rtl detect vrf write");
      add_rtl_write(se_vrf_write, lane_idx, vd, offset, mask, data, idx);
    }
  } // end if(valid)
}

void VBridgeImpl::record_rf_queue_accesses(
    const VLsuWriteQueuePeek &lsu_queue) {
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
    Log("RecordRFAccesses")
        .with("lane", lane_idx)
        .with("vd", vd)
        .with("offset", offset)
        .with("mask", fmt::format("{:04b}", mask))
        .with("data", fmt::format("{:08X}", data))
        .with("instruction_index", idx)
        .info("rtl detect vrf queue write");
    CHECK_NE(se_vrf_write, nullptr,
             fmt::format("cannot find se with issue_idx {}", idx));
    add_rtl_write(se_vrf_write, lane_idx, vd, offset, mask, data, idx);
  }
}

void VBridgeImpl::add_rtl_write(SpikeEvent *se, uint32_t lane_idx, uint32_t vd,
                                uint32_t offset, uint32_t mask, uint32_t data,
                                uint32_t idx) {
  uint32_t record_idx_base =
      vd * config.v_len_in_bytes + (lane_idx + config.lane_number * offset) * 4;
  auto &all_writes = se->vrf_access_record.all_writes;

  for (int j = 0; j < 32 / 8; j++) { // 32bit / 1byte
    if ((mask >> j) & 1) {
      uint8_t written_byte = (data >> (8 * j)) & 0xff;
      auto record_iter = all_writes.find(record_idx_base + j);

      if (record_iter != all_writes.end()) { // if find a spike write record
        auto &record = record_iter->second;
        CHECK_EQ((int)record.byte, (int)written_byte,
                 fmt::format( // convert to int to avoid stupid printing
                     "{}th byte incorrect ({:02X} != {:02X}) for vrf "
                     "write (lane={}, vd={}, offset={}, mask={:04b}) "
                     "[vrf_idx={}]",
                     j, record.byte, written_byte, lane_idx, vd,
                     offset, mask, record_idx_base + j));
        record.executed = true;

      } else if (uint8_t original_byte = vrf_shadow[record_idx_base + j];
                 original_byte != written_byte) {
//        FATAL(fmt::format(
//            "vrf writes {}th byte (lane={}, vd={}, offset={}, "
//            "mask={:04b}, data={}, original_data={}), "
//            "but not recorded by spike ({}) [{}]",
//            j, lane_idx, vd, offset, mask, written_byte, original_byte,
//            se->describe_insn(), record_idx_base + j));
      } else {
        // no spike record and rtl written byte is identical as the byte before
        // write, safe
      }

      vrf_shadow[record_idx_base + j] = written_byte;
    } // end if mask
  }   // end for j
}

#ifndef COSIM_NO_DRAMSIM
void VBridgeImpl::dramsim_drive(const int channel_id) {
  auto &[dram, tick] = drams[channel_id];
  const auto target_dram_tick = get_t() * tck / dram.GetTCK();
  while(tick < target_dram_tick) {
    ++tick;
    dram.ClockTick();

    // Presents request, look for first request that's not fully sent
    for(auto &[tick, req] : tl_req_record_of_bank[channel_id]) {
      if(!req.done_commit()) {
        // Found head of queue, check eligibility

        auto burst_size = dramsim_burst_size(channel_id);
        auto dram_req = req.issue_mem_request(burst_size);

        if(dram_req && dram.WillAcceptTransaction(dram_req->first, dram_req->second)) {
          // std::cout<<"Add transaction "<<fmt::format("0x{:08x}", dram_req->first)<<std::endl;
          dram.AddTransaction(dram_req->first, dram_req->second);
          req.commit_mem_request(burst_size);
        }

        break;
      }
    }

    // std::cout<<"Digest of channel "<<channel_id<<std::endl;
    // for(auto &[_, req] : tl_req_record_of_bank[channel_id])
    //   req.format();
    // std::cout<<"================"<<std::endl;

  }
}

void VBridgeImpl::dramsim_resolve(const int channel_id, reg_t addr) {
  if(tl_req_record_of_bank[channel_id].empty())
    FATAL(fmt::format("Response on an idle channel {}", channel_id));

  bool found = false;
  for(auto &[_, req] : tl_req_record_of_bank[channel_id])
    if(req.resolve_mem_response(addr, dramsim_burst_size(channel_id))) {
      // std::cout<<"After resolution"<<std::endl;
      // req.format();
      found = true;
      break;
    }

  if(!found)
    FATAL(fmt::format("dram response no matching request: 0x{:08x}", addr));
}

size_t VBridgeImpl::dramsim_burst_size(const int channel_id) const {
  return drams[channel_id].first.GetBurstLength() * drams[channel_id].first.GetBusBits() / 8;
}
#endif // COSIM_NO_DRAMSIM

VBridgeImpl vbridge_impl_instance;
