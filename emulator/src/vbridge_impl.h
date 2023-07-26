#pragma once

#include <algorithm>
#include <queue>
#include <optional>
#include <mutex>
#include <thread>
#include <condition_variable>
#include <list>

#include <mmu.h>

#ifdef COSIM_VERILATOR
  #include <verilated.h>
  #include <verilated_cov.h>

  #if VM_TRACE
    #include <verilated_fst_c.h>
  #endif

  #include <svdpi.h>
#endif

#include "spike_event.h"
#include "simple_sim.h"
#include "encoding.h"
#include "util.h"
#include "rtl_config.h"

class SpikeEvent;

struct TLReqRecord {
  uint64_t data;
  uint32_t size_by_byte;
  uint16_t source;

  /// when opType set to nil, it means this record is already sent back
  enum class opType {
    Nil, Get, PutFullData
  } op;
  int remaining_cycles;

  TLReqRecord(uint64_t data, uint32_t size_by_byte, uint16_t source, opType op, int cycles) :
      data(data), size_by_byte(size_by_byte), source(source), op(op), remaining_cycles(cycles) {};
};

struct TLMemCounterRecord {
  uint32_t counter;
  uint32_t decoded_size;
  uint32_t addr;
};

class VBridgeImpl {
public:
  VBridgeImpl();

#if VM_TRACE
  void dpiDumpWave();
#endif

  void dpiInitCosim();

  void timeoutCheck();

  uint8_t load(uint64_t address);

  uint64_t get_t();

  // Simulator Calls
#ifdef COSIM_VERILATOR
  uint64_t getCycle() {
    return ctx->time();
  }
  void getCoverage() {
    return ctx->coveragep()->write();
  }
#endif

  void dpiPokeInst(const VInstrInterfacePoke &v_instr, const VCsrInterfacePoke &v_csr, const VRespInterface &v_resp);
  void dpiPokeTL(const VTlInterfacePoke &v_tl_poke);
  void dpiPeekTL(const VTlInterface &v_tl);
  void dpiPeekWriteQueue(const VLsuWriteQueuePeek &v_enq);
  void dpiPeekIssue(svBit ready, svBitVecVal issueIdx);
  void dpiPeekLsuEnq(const VLsuReqEnqPeek &lsu_req_enq);
  void dpiPeekVrfWrite(const VrfWritePeek &v_enq);

  RTLConfig config;

private:

  std::string varch;
  cfg_t cfg;
  simple_sim sim;
  isa_parser_t isa;
  processor_t proc;
  std::vector<std::multimap<reg_t, TLReqRecord>> tl_banks;
  std::vector<std::optional<reg_t>> tl_current_req;
  std::vector<TLMemCounterRecord> tl_mem_store_counter;

  SpikeEvent *se_to_issue;

  /// to rtl stack
  /// in the spike thread, spike should detech if this queue is full, if not full, execute until a vector instruction,
  /// record the behavior of this instruction, and send to str_stack.
  /// in the RTL thread, the RTL driver will consume from this queue, drive signal based on the queue.
  /// size of this queue should be as big as enough to make rtl free to run, reducing the context switch overhead.
  std::list<SpikeEvent> to_rtl_queue;

  // in vrf_shadow we keep a duplicate of vrf in rtl, in order to detect unexpected vrf write
  std::unique_ptr<uint8_t[]> vrf_shadow;

  /// file path of executable binary file, which will be executed.
  const std::string bin = get_env_arg("COSIM_bin");

  /// generated waveform path.
  const std::string wave = get_env_arg("COSIM_wave");

  /// RTL timeout cycles
  /// note: this is not the real system cycles, scalar instructions is evaulated via spike, which is not recorded.
  const uint64_t timeout = std::stoul(get_env_arg("COSIM_timeout"));

  std::optional<SpikeEvent> create_spike_event(insn_fetch_t fetch);

  std::optional<SpikeEvent> spike_step();
  SpikeEvent *find_se_to_issue();

  // for load instructions, rtl would commit after vrf write requests enters the write queue (but not actually written),
  // hence for these instructions, we should record vrf write on queue, and ignore them on vrf.write
  void record_rf_queue_accesses(const VLsuWriteQueuePeek &lsu_queues);
  void record_rf_accesses(const VrfWritePeek &rf_writs);
  void receive_tl_d_ready(const VTlInterface &tl);
  void return_tl_response(const VTlInterfacePoke &tl_poke);
  void receive_tl_req(const VTlInterface &tl);
  void update_lsu_idx(const VLsuReqEnqPeek &enq);

  void add_rtl_write(SpikeEvent *se, uint32_t lane_idx, uint32_t vd, uint32_t offset, uint32_t mask, uint32_t data, uint32_t idx);

  // simulator context
#ifdef COSIM_VERILATOR
  VerilatedContext *ctx;

#if VM_TRACE
  VerilatedFstC tfp;
#endif

#endif

  int get_mem_req_cycles() {
    return 1;
  };

  uint64_t last_commit_time = 0;
};

extern VBridgeImpl vbridge_impl_instance;
