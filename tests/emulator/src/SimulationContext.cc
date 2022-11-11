#include "SimulationContext.h"

SimulationContext &SimulationContext::getInstance() {
  static SimulationContext singleton;
  return singleton;
}

SimulationContext::SimulationContext() : proc(
  /*isa*/ &isa,
  /*varch*/ fmt::format("vlen:{},elen:{}", consts::vlen_in_bits, consts::elen).c_str(),
  /*sim*/  (simif_t *) &sim,
  /*id*/ 0,
  /*halt on reset*/ true,
  /* endianness*/ memif_endianness_little,
  /*log_file_t*/ nullptr,
  /*sout*/ std::cerr
) {
  proc.reset();
  sim.load(bin, resetvector);
}