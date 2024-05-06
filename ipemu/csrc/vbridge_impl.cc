#include <filesystem>

#include <fmt/core.h>
#include <fmt/ranges.h>
#include <args.hxx>
#include <utility>

#include <disasm.h>
#include <decode_macros.h>

#include <verilated.h>

#include "exceptions.h"
#include "spdlog_ext.h"
#include "util.h"
#include "vbridge_impl.h"

/// convert TL style size to size_by_bytes
inline uint32_t decode_size(uint32_t encoded_size) { return 1 << encoded_size; }

inline bool is_pow2(uint32_t n) { return n && !(n & (n - 1)); }

void VBridgeImpl::timeoutCheck() {
  getCoverage();
#if VM_TRACE
  if(get_t() >= dump_from_cycle && !dump_start) {
    dpiDumpWave();
    dump_start = true;
  }
#endif
  CHECK_LE(get_t(), timeout + last_commit_time,
           fmt::format("Simulation timeout, last_commit: {}", last_commit_time));
}

void VBridgeImpl::dpiInitCosim() {
  ctx = Verilated::threadContextp();
  proc.reset();
  // TODO: remove this line, and use CSR write in the test code to enable this
  // the VS field.
  proc.get_state()->sstatus->write(proc.get_state()->sstatus->read() |
                                   SSTATUS_VS | SSTATUS_FS);

  auto load_result = sim.load_elf32_little_endian(bin);

  proc.get_state()->pc = load_result.entry_addr;

  Log("DPIInitCosim")
    .with("bin", bin)
    .with("wave", wave)
    .with("timeout", timeout)
    .with("entry", fmt::format("{:08X}", load_result.entry_addr))
    .info("Simulation environment initialized");
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
      Log("DPIPokeInst").trace("exit waiting for fence");
    } else {
      Log("DPIPokeInst")
          .trace("waiting for fence, no issuing new instruction");
    }
    *v_instr.valid = false;
  } else {
    Log("DPIPokeInst")
        .with("inst", se_to_issue->jsonify_insn())
        .with("rs1", fmt::format("{:08X}", se_to_issue->rs1_bits))
        .with("rs2", fmt::format("{:08X}", se_to_issue->rs2_bits))
        .trace("poke instruction");
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

  if(using_dramsim3)
    dramsim_drive(v_tl_resp.channel_id);
}

//==================
// posedge (2)
//==================

void VBridgeImpl::dpiPeekIssue(svBit ready, svBitVecVal issueIdx) {
  if (ready && !(se_to_issue->is_vfence_insn || se_to_issue->is_exit_insn)) {
    se_to_issue->is_issued = true;
    se_to_issue->issue_idx = issueIdx;
    Log("DPIPeekIssue")
        .with("insn", se_to_issue->jsonify_insn())
        .with("vl", se_to_issue->vl)
        .with("vsew", se_to_issue->vsew)
        .with("vlmul", se_to_issue->vlmul)
        .with("rs1", fmt::format("{:08X}", se_to_issue->rs1_bits))
        .with("rs2", fmt::format("{:08X}", se_to_issue->rs2_bits))
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

static VBridgeImpl vbridgeImplFromArgs() {
  std::ifstream cmdline("/proc/self/cmdline");
  std::vector<std::string> arg_vec;
  for (std::string line; std::getline(cmdline, line, '\0');) {
    arg_vec.emplace_back(line);
  }
  std::vector<const char*> argv;
  argv.reserve(arg_vec.size());
  for (const auto& arg: arg_vec) {
    argv.emplace_back(arg.c_str());
  }

  args::ArgumentParser parser("emulator for t1");

  args::Flag no_logging(parser, "no_logging", "Disable all logging utilities.", { "no-logging" });
  args::Flag no_file_logging(parser, "no_file_logging", "Disable file logging utilities.", { "no-file-logging" });
  args::Flag no_console_logging(parser, "no_console_logging", "Disable console logging utilities.", { "no-console-logging" });
  args::ValueFlag<std::string> log_path(parser, "log path", "Path to store logging file", {"log-path"});

  args::ValueFlag<size_t> vlen(parser, "vlen", "match from RTL config, tobe removed", {"vlen"}, args::Options::Required);
  args::ValueFlag<size_t> dlen(parser, "dlen", "match from RTL config, tobe removed", {"dlen"}, args::Options::Required);
  args::ValueFlag<size_t> tl_bank_number(parser, "tl_bank_number", "match from RTL config, tobe removed", {"tl_bank_number"}, args::Options::Required);
  args::ValueFlag<size_t> beat_byte(parser, "beat_byte", "match from RTL config, tobe removed", {"beat_byte"}, args::Options::Required);

  args::ValueFlag<std::string> bin_path(parser, "elf path", "", {"elf"}, args::Options::Required);
  args::ValueFlag<std::string> wave_path(parser, "wave path", "", {"wave"}, args::Options::Required);
  args::ValueFlag<std::optional<std::string>> perf_path(parser, "perf path", "", {"perf"});
  args::ValueFlag<uint64_t> timeout(parser, "timeout", "", {"timeout"}, args::Options::Required);
#if VM_TRACE
  args::ValueFlag<uint64_t> dump_from_cycle(parser, "dump_from_cycle", "start to dump wave at cycle", {"dump-from-cycle"}, args::Options::Required);
#endif
  args::ValueFlag<double> tck(parser, "tck", "", {"tck"}, args::Options::Required);
  args::Group dramsim_group(parser, "dramsim config", args::Group::Validators::AllOrNone);
  args::ValueFlag<std::optional<std::string>> dramsim3_config_path(dramsim_group, "config path", "", {"dramsim3-config"});
  args::ValueFlag<std::optional<std::string>> dramsim3_result_dir(dramsim_group, "result dir", "", {"dramsim-result"});

  try {
    parser.ParseCLI((int) argv.size(), argv.data());
  } catch (args::Help&) {
    std::cerr << parser;
    std::exit(0);
  } catch (args::Error& e) {
    std::cerr << e.what() << std::endl << parser;
    std::exit(1);
  }

  Log = JsonLogger(no_logging.Get(), no_file_logging.Get(), no_console_logging.Get(), log_path.Get());

  Config cosim_config {
    .bin_path = bin_path.Get(),
    .wave_path = wave_path.Get(),
    .perf_path = perf_path.Get(),
    .timeout = timeout.Get(),
#if VM_TRACE
    .dump_from_cycle = dump_from_cycle.Get(),
#endif
    .tck = tck.Get(),
    .dramsim3_config_path = dramsim3_config_path.Get(),
    .dramsim3_result_dir = dramsim3_result_dir.Get(),
    .vlen = vlen.Get(),
    .dlen = dlen.Get(),
    .tl_bank_number = tl_bank_number.Get(),
    // TODO: clean me up
    .datapath_width = 32,
    .lane_number = dlen.Get() / 32,
    .elen = 32,
    .vreg_number = 32,
    .mshr_number = 3,
    .lsu_idx_default = 255,
    .vlen_in_bytes = vlen.Get() / 8,
    .datapath_width_in_bytes = beat_byte.Get(),
  };

  return VBridgeImpl { cosim_config };
}

cfg_t make_spike_cfg(const std::string &varch) {
  cfg_t cfg;
  cfg.initrd_bounds = std::make_pair((reg_t)0, (reg_t)0),
  cfg.bootargs = nullptr;
  cfg.isa = DEFAULT_ISA;
  cfg.priv = DEFAULT_PRIV;
  cfg.varch = varch.data();
  cfg.misaligned = false;
  cfg.endianness = endianness_little;
  cfg.pmpregions = 16;
  cfg.pmpgranularity = 4;
  cfg.mem_layout = std::vector<mem_cfg_t>();
  cfg.hartids = std::vector<size_t>();
  cfg.explicit_hartids = false;
  cfg.real_time_clint = false;
  cfg.trigger_count = 4;
  return cfg;
}

VBridgeImpl::VBridgeImpl(Config cosim_config)
    : config(std::move(cosim_config)),
      varch(fmt::format("vlen:{},elen:{}", config.vlen, config.elen)),
      sim(1l << 32), isa("rv32gcv", "M"),
      cfg(make_spike_cfg(varch)),
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
      bin(config.bin_path),
      wave(config.wave_path),
      perf_path(config.perf_path),
      timeout(config.timeout),
#ifdef VM_TRACE
      dump_from_cycle(config.dump_from_cycle),
#endif
      tck(config.tck),

#ifdef COSIM_VERILATOR
      ctx(nullptr),
#endif
      vrf_shadow(std::make_unique<uint8_t[]>(config.vlen_in_bytes *
                                             config.vreg_number))
                                             {
  proc.VU.lane_num = config.lane_number;
  proc.VU.lane_granularity = 32;

  auto &csrmap = proc.get_state()->csrmap;
  csrmap[CSR_MSIMEND] = std::make_shared<basic_csr_t>(&proc, CSR_MSIMEND, 0);
  proc.enable_log_commits();

  this->using_dramsim3 = config.dramsim3_config_path.has_value();

  if(this->using_dramsim3) {
    for(int i = 0; i < config.tl_bank_number; ++i) {
      std::string result_dir = config.dramsim3_config_path.value() + "/channel." + std::to_string(i);
      std::filesystem::create_directories(result_dir);
      auto completion = [i, this](uint64_t address) {
        this->dramsim_resolve(i, address);
      };

      drams.emplace_back(dramsim3::MemorySystem(config.dramsim3_config_path.value(), result_dir, completion, completion), 0);
      // std::cout<<"Relative tck ratio on channel "<<i<<" = "<<tck / drams[i].first.GetTCK()<<std::endl;
    }
  }
}

#ifdef COSIM_VERILATOR
uint64_t VBridgeImpl::get_t() {
  if (ctx) {
    return ctx->time();
  } else {  // before ctx is initialized
    return 0;
  }
}
void VBridgeImpl::getCoverage() { return ctx->coveragep()->write(); }
#endif

std::optional<SpikeEvent> VBridgeImpl::spike_step() {
  auto state = proc.get_state();

  state->mcycle->write((int64_t) get_t() + spike_cycles);

  auto fetch = proc.get_mmu()->load_insn(state->pc);
  auto event = create_spike_event(fetch);

  clear_state(proc);

  reg_t old_pc = state->pc;
  reg_t new_pc;
  if (event) {
    auto &se = event.value();
    Log("SpikeStep")
        .with("insn", se.jsonify_insn())
        .with("vl", se.vl)
        .with("sew", (int)se.vsew)
        .with("lmul", (int)se.vlmul)
        .with("pc", fmt::format("{:08X}", se.pc))
        .with("rs1", fmt::format("{:08X}", se.rs1_bits))
        .with("rs2", fmt::format("{:08X}", se.rs2_bits))
        .with("spike_cycles", spike_cycles)
        .info("spike run vector insn");
    se.pre_log_arch_changes();
    new_pc = fetch.func(&proc, fetch.insn, state->pc);
    se.log_arch_changes();
  } else {
    auto disasm = proc.get_disassembler()->disassemble(fetch.insn);
    Log("SpikeStep")
        .with("pc", fmt::format("{:08X}", state->pc))
        .with("bits", fmt::format("{:08X}", fetch.insn.bits()))
        .with("disasm", disasm)
        .with("spike_cycles", spike_cycles)
        .info("spike run scalar insn");
    new_pc = fetch.func(&proc, fetch.insn, state->pc);

    if (disasm == "ret" && !frames.empty() && new_pc == frames.back().return_addr) {
      Log("FunctionCall")
        .with("old_pc", fmt::format("{:08X}", old_pc))
        .with("new_pc", fmt::format("{:08X}", new_pc))
        .with("spike_cycles", spike_cycles)
        .with("depth", frames.size())
        .info("return");
      frames.pop_back();
    }
  }

  if (new_pc - state->pc != 2 && new_pc - state->pc != 4) {
    auto sym_find = sim.get_symbol(new_pc);
    if (sym_find != nullptr) {
      Log("FunctionCall")
        .with("func_name", sym_find)
        .with("old_pc", fmt::format("{:08X}", old_pc))
        .with("new_pc", fmt::format("{:08X}", new_pc))
        .with("spike_cycles", spike_cycles)
        .with("depth", frames.size())
        .info("call");
      reg_t return_addr = state->XPR[1];
      frames.emplace_back(CallFrame{sym_find, new_pc, return_addr, spike_cycles});
    }
  }

  // Bypass CSR insns commitlog stuff.
  if ((new_pc & 1) == 0) {
    state->pc = new_pc;
  } else {
    Log("SpikeStep")
      .with("new_pc", fmt::format("{:08X}", new_pc))
      .with("priv", state->prv)
      .with("ext", proc.extension_enabled_const('U'))
      .info("CSR serialize");
    switch (new_pc) {
    case PC_SERIALIZE_BEFORE:
      state->serialized = true;
      break;
    case PC_SERIALIZE_AFTER:
      break;
    default:
      FATAL(fmt::format("SpikeStep: invalid new_pc: {:08X}", new_pc));
    }
  }

  // spike does not bump mcycle by itself, so do it manually
  spike_cycles ++;

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
    return SpikeEvent{proc, fetch, this, config.lsu_idx_default };
  } else {
    return {};
  }
}

uint8_t VBridgeImpl::load(uint64_t address) {
  return *sim.addr_to_mem(address);
}

void VBridgeImpl::receive_tl_req(const VTlInterface &tl) {
  uint32_t tlIdx = tl.channel_id;
  if (!tl.a_valid)
    return;

  uint8_t opcode = tl.a_bits_opcode;
  uint32_t base_addr = tl.a_bits_address;

  size_t size_encoded = tl.a_bits_size;
  size_t size = decode_size(size_encoded);
  uint16_t src = tl.a_bits_source; // MSHR id, TODO: be returned in D channel
  uint32_t lsu_index = tl.a_bits_source & 3;
  const uint32_t *mask = tl.a_bits_mask;
  SpikeEvent *se = nullptr;
  for (auto se_iter = to_rtl_queue.begin(); se_iter != to_rtl_queue.end(); se_iter++) {
    if (se_iter->lsu_idx == lsu_index) {
      se = &(*se_iter);
      break;
    }
  }

  CHECK(se != nullptr, fmt::format("[{}] cannot find SpikeEvent with lsu_idx={}",
                        get_t(), lsu_index));
  CHECK_EQ((base_addr & (size - 1)), 0,
           fmt::format("[{}] unaligned access (addr={:08X}, size={})",
                       get_t(), base_addr, size));

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
            .with("addr", fmt::format("{:08X}", addr))
            .with("insn", se->jsonify_insn())
            .info("send falsy data 0xDE for accessing unexpected memory");
        actual_data[offset] = 0xDE; // falsy data
      }
    }

    Log("ReceiveTLReq")
        .with("channel", tlIdx)
        .with("bass_addr", fmt::format("{:08X}", base_addr))
        .with("size_by_byte", size)
        .with("mask", fmt::format("{:b}", *mask))
        .with("src", fmt::format("{:04X}", src))
        .with("return_data", fmt::format("{:02X}", fmt::join(actual_data, " ")))
        .info("<- receive rtl mem get req");

    auto emplaced = tl_req_record_of_bank[tlIdx].emplace(
        get_t(), TLReqRecord{se, get_t(), actual_data, size, base_addr, src,
                             TLReqRecord::opType::Get, dramsim_burst_size(tlIdx)});
    if(!this->using_dramsim3) emplaced->second.skip();
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
          TLReqRecord{se, get_t(), std::vector<uint8_t>(size), size, base_addr, src,
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
        .with("base_addr", fmt::format("{:08X}", base_addr))
        .with("offset", data_begin_pos)
        .with("size_by_byte", size)
        .with("src", fmt::format("{:04X}", src))
        .with("data",
              fmt::format("{:02X}",
                          fmt::join(data.begin() + (long)data_begin_pos,
                                    data.begin() + (long)(data_begin_pos +
                                                          actual_beat_size),
                                    " ")))
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
                 fmt::format("[{}] cannot find mem write of byte_addr {:08X}",
                             get_t(), byte_addr));
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
                 fmt::format("[{}] expect mem write of byte {:02X}, actual "
                             "byte {:02X} (channel={}, byte_addr={:08X}, {})",
                             get_t(), single_mem_write.val, tl_data_byte, tlIdx,
                             byte_addr, se->describe_insn()));
      }
    }

    cur_record->bytes_received += actual_beat_size;
    if(!this->using_dramsim3) cur_record->skip();

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
  uint32_t tlIdx = tl.channel_id;

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
    d_valid = returned_resp.has_value();
    if(d_valid) {
      auto &record = next_return->second;
      auto [offset, transfer_size] = *returned_resp;
      Log("ReturnTlResponse")
          .with("channel", i)
          .with("addr", fmt::format("{:08X}", record.addr))
          .with("size_by_byte", record.size_by_byte)
          .with("src", fmt::format("{:04X}", record.source))
          .with("data",
                fmt::format(
                    "{:02X}",
                    fmt::join(record.data.begin() + (long) offset,
                              record.data.begin() +
                                  (long)(offset + transfer_size),
                              " ")))
          .with("offset", offset)
          .with("lsu_idx", record.se->lsu_idx)
          .info("-> send tl response");

      *tl_poke.d_bits_opcode = record.op == TLReqRecord::opType::Get
                                   ? TlOpcode::AccessAckData
                                   : TlOpcode::AccessAck;

      for (size_t ioffset = 0; ioffset < transfer_size; ioffset++) {
        // for GET request not aligned to data bus, put it to a correct byte
        // lane
        size_t byte_lane_idx = (record.addr + ioffset) % config.datapath_width_in_bytes;
        ((uint8_t *)tl_poke.d_bits_data)[byte_lane_idx] = record.data[offset + ioffset];
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
        (se->lsu_idx == config.lsu_idx_default)) {
      uint8_t index = config.lsu_idx_default;
      for (int i = 0; i < config.mshr_number; i++) {
        if (lsuReqs[i] == 1) {
          index = i;
          break;
        }
      }
      if (index == config.lsu_idx_default) {
        Log("UpdateLSUIdx")
            .trace("waiting for lsu request to fire");
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
    FATAL(fmt::format("spike trapped with {} (tval={:X}, tval2={:X}, tinst={:X})",
      trap.name(), trap.get_tval(), trap.get_tval2(), trap.get_tinst()));
  }
}

void VBridgeImpl::record_rf_accesses(const VrfWritePeek &rf_write) {
  int valid = rf_write.valid;
  uint32_t lane_idx = rf_write.lane_index;
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
          .info("rtl detect vrf write which cannot find se, maybe from "
                "committed load insn");
    } else if (!se_vrf_write->is_load) {
      Log("RecordRFAccess")
          .with("lane", lane_idx)
          .with("vd", vd)
          .with("offset", offset)
          .with("mask", fmt::format("{:04b}", mask))
          .with("data", fmt::format("{:08X}", data))
          .with("issue_idx", idx)
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
        .with("issue_idx", idx)
        .info("rtl detect vrf queue write");
    CHECK_NE(se_vrf_write, nullptr,
             fmt::format("[{}] cannot find se with issue_idx {}", get_t(), idx));
    add_rtl_write(se_vrf_write, lane_idx, vd, offset, mask, data, idx);
  }
}

void VBridgeImpl::add_rtl_write(SpikeEvent *se, uint32_t lane_idx, uint32_t vd,
                                uint32_t offset, uint32_t mask, uint32_t data,
                                uint32_t idx) {
  uint32_t record_idx_base =
      vd * config.vlen_in_bytes + (lane_idx + config.lane_number * offset) * 4;
  auto &all_writes = se->vrf_access_record.all_writes;

  for (int j = 0; j < 32 / 8; j++) { // 32bit / 1byte
    if ((mask >> j) & 1) {
      uint8_t written_byte = (data >> (8 * j)) & 0xff;
      auto record_iter = all_writes.find(record_idx_base + j);

      if (record_iter != all_writes.end()) { // if find a spike write record
        auto &record = record_iter->second;
        CHECK_EQ((int)record.byte, (int)written_byte,
                 fmt::format( // convert to int to avoid stupid printing
                     "[{}] {}th byte incorrect ({:02X} != {:02X}) for vrf "
                     "write (lane={}, vd={}, offset={}, mask={:04b}) "
                     "[vrf_idx={}] (lsu_idx={}, {})",
                     get_t(), j, record.byte, written_byte, lane_idx, vd,
                     offset, mask, record_idx_base + j, se->lsu_idx, se->describe_insn()));
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

void VBridgeImpl::dramsim_drive(uint32_t channel_id) {
  auto &[dram, tick] = drams[channel_id];
  const auto target_dram_tick = (double) get_t() * tck / dram.GetTCK();
  while((double) tick < target_dram_tick) {
    ++tick;
    dram.ClockTick();

    // Presents request, look for first request that's not fully sent
    for(auto &[_, req] : tl_req_record_of_bank[channel_id]) {
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

void VBridgeImpl::dramsim_resolve(uint32_t channel_id, reg_t addr) {
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
    FATAL(fmt::format("dram response no matching request: 0x{:08X}", addr));
}

size_t VBridgeImpl::dramsim_burst_size(uint32_t channel_id) const {
  if(!using_dramsim3) return 1; // Dummy value, won't be effective whatsoever. 1 is to ensure that no sub-line write is possible
  return drams[channel_id].first.GetBurstLength() * drams[channel_id].first.GetBusBits() / 8;
}

void VBridgeImpl::on_exit() {
  // TODO: This is used in CI.
  if (perf_path.has_value()) {
    std::ofstream of(perf_path->c_str());
    of << fmt::format("total_cycles: {}\n", Verilated::threadContextp()->time() / 10);
    of.close();
    Log("PrintPerfSummary")
      .with("path", perf_path.value())
      .info("Perf result saved");
  }
}


VBridgeImpl vbridge_impl_instance = vbridgeImplFromArgs();
