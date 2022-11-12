#pragma once
#include <fmt/core.h>
#include <glog/logging.h>
#include <list>
#include <svdpi.h>

#include "glog_exception_safe.h"
#include "simple_sim.h"
#include "vbridge_config.h"

#include "mmu.h"
#include "isa_parser.h"
#include "SpikeEvent.h"

// TODO macro this
#include <verilated.h>

class SimulationContext {
public:
  static SimulationContext& getInstance();

  uint8_t load(uint64_t address);
  // DPI Calls
  void pokeInstruction(svBitVecVal *inst, svBitVecVal *s1, svBitVecVal *s2, svBit* valid);
  void instructionFire(const svBitVecVal* index);
  void initCosim();
  void timeoutCheck();
  void peekResponse(const svBitVecVal* bits);
  void pokeCSR(svBitVecVal* vl, svBitVecVal* vStart, svBitVecVal* vlmul, svBitVecVal* vSew, svBitVecVal* vxrm, svBit* vta, svBit* vma, svBit* ignoreException);


  // Simulator Calls
  // TODO: macro this
  uint64_t get_t() {
    return simulatorContext->time();
  }
private:
  SimulationContext();
  // Get configurations from environment variables
  const std::string bin = std::getenv("COSIM_bin");
  const std::string wave = std::getenv("COSIM_wave");
  const uint64_t reset_vector = std::stoul(std::getenv("COSIM_reset_vector"), 0, 16);
  const uint64_t timeout = std::stoul(std::getenv("COSIM_timeout"));
  const uint64_t spike_event_queue_size = std::stoul(std::getenv("COSIM_spike_event_queue_size"));

  // spike context
  const isa_parser_t isa;
  simple_sim sim;
  processor_t proc;
  std::list<SpikeEvent> spike_event_queue;
  void loop_until_se_queue_full();
  std::optional<SpikeEvent> spike_step();
  std::optional<SpikeEvent> create_spike_event(insn_fetch_t fetch);
  static inline uint32_t clip(uint32_t binary, int a, int b);
  SpikeEvent *find_se_to_issue();

  // singleton check
  bool instantiated = false;

  // TODO: macro this
  // simulator context
  VerilatedContext* simulatorContext = Verilated::threadContextp();
};

