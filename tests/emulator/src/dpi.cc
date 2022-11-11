#include "VTestBench__Dpi.h"

#include <fmt/core.h>
#include <glog/logging.h>
#include "SimulationContext.h"

void dpiFire() {
  
}

void dpiInitCosim() {
  google::InitGoogleLogging("cosim");
  google::InstallFailureSignalHandler();
  auto &ctx = SimulationContext::getInstance();
  VLOG(0) << fmt::format("Simulation Environment Initialized: bin={}, wave={}, resetvector={:#x}, timeout={}", ctx.bin, ctx.wave, ctx.resetvector, ctx.timeout);
}