#ifndef _RISCV_VBRIDGE_H
#define _RISCV_VBRIDGE_H

#include <cstddef>

#include "decode.h"
#include "processor.h"
#include "simple_sim.h"

struct VBridgeImpl;

struct VBridge {
  VBridgeImpl *impl;

  VBridge(processor_t &proc, simple_sim &sim);

  ~VBridge();

  void setup(int argc, char **argv) const;

  void loop() const;
};

#endif
