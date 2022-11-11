#pragma once
#include <fmt/core.h>
#include <glog/logging.h>
#include "glog_exception_safe.h"
#include "simple_sim.h"
#include "mmu.h"
#include "simple_sim.h"
#include "isa_parser.h"
#include "vbridge_config.h"


class SimulationContext {
public:
  static SimulationContext& getInstance();
  const std::string bin = std::getenv("COSIM_bin");
  const std::string wave = std::getenv("COSIM_wave");
  const uint64_t resetvector = std::stoul(std::getenv("COSIM_resetvector"), 0, 16);
  const uint64_t timeout = std::stoul(std::getenv("COSIM_timeout"));
  const isa_parser_t isa = isa_parser_t("rv32gcv", "M");
  simple_sim sim = simple_sim(1 << 30);
  processor_t proc;

private:
  SimulationContext();
};

