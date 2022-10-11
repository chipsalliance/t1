#ifndef _RISCV_VBRIDGE_H
#define _RISCV_VBRIDGE_H

#include <cstddef>

#include "decode.h"
#include "processor.h"
#include "simple_sim.h"

class VerilatedContext;
struct VBridgeImpl;

struct VBridge {
  VBridgeImpl *impl;

  VBridge(processor_t &proc, simple_sim &sim);

  ~VBridge();

  void setup(const std::string &bin, const std::string &vcd, uint64_t reset_vector, uint64_t cycles) const;

  void loop() const;

  VerilatedContext &get_verilator_ctx() const;
};

#endif
