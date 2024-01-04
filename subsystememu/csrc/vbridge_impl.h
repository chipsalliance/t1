#pragma once

#include <condition_variable>
#include <list>
#include <mutex>
#include <optional>
#include <queue>
#include <thread>
#include <utility>

#include <mmu.h>

#ifdef COSIM_VERILATOR
#include <verilated.h>
#include <verilated_cov.h>

#if VM_TRACE
#include <verilated_fst_c.h>
#endif

#include <svdpi.h>
#endif

#include "encoding.h"
#include "rtl_config.h"
#include "simple_sim.h"
#include "spike_event.h"

#include "util.h"

class SpikeEvent;

class VBridgeImpl {
public:
  VBridgeImpl();



  void dpiInitCosim();

  void dpiRefillQueue();

  void dpiCommitPeek(svBit ll_wen,
                     svBit rf_wen,
                     svBit wb_valid,
                     svBitVecVal rf_waddr,
                     svBitVecVal rf_wdata,
                     svBitVecVal wb_reg_pc,
                     svBitVecVal wb_reg_inst);
  void check_rf_write(svBit ll_wen,
                     svBit rf_wen,
                     svBit wb_valid,
                     svBitVecVal rf_waddr,
                     svBitVecVal rf_wdata,
                     svBitVecVal wb_reg_pc,
                     svBitVecVal wb_reg_inst);

  void timeoutCheck();

  uint8_t load(uint64_t address);

  uint64_t get_t();

//  // Simulator Calls
//#ifdef COSIM_VERILATOR
//  uint64_t getCycle() { return ctx->time(); }
//  void getCoverage() { return ctx->coveragep()->write(); }
//#endif


  RTLConfig config;

private:
  std::string varch;
  cfg_t cfg;
  simple_sim sim;
  isa_parser_t isa;
  processor_t proc;



  /// file path of executable binary file, which will be executed.
  const std::string bin = get_env_arg("COSIM_bin");

  //Spike
  const size_t to_rtl_queue_size = 10;
  std::list<SpikeEvent> to_rtl_queue;
  void loop_until_se_queue_full();

  std::optional<SpikeEvent> spike_step();
  std::optional<SpikeEvent> create_spike_event(insn_fetch_t fetch);

  // for muti cycles inst
  bool waitforMutiCycleInsn;
  uint32_t pendingInsn_pc;
  uint32_t pendingInsn_waddr;
  uint64_t pendingInsn_wdata;


//  // simulator context
//#ifdef COSIM_VERILATOR
//  VerilatedContext *ctx;
//
//#if VM_TRACE
//  VerilatedFstC tfp;
//#endif
//
//#endif

  int get_mem_req_cycles() { return 1; };

  uint64_t last_commit_time = 0;
};

extern VBridgeImpl vbridge_impl_instance;
