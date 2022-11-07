#pragma once

#include <cstddef>
#include <string>
#include <memory>

struct VBridgeImpl;

// Vector Instruction Buffer:
//   diff: record VRF Write, Mem write
//   drive: instr, csr, Mem load
//
// Spike enqueue buffer when not empty.
// for each cycle, verilator capture signals, and use Vector Instruction Buffer to difftest
//   after commit, dequeue VIB
//
// Verilator with boring -> Verilator with VPI
// get_signals(&some_struct) {
//   // access with IO
//   some_struct->xxx = top.debug.xxx
//   // access with VPI
//   vpi_get_value(vh1, &v)
// }
//
// VCS
// DPI set_signals() {
//   VIB vib;
//   // use vib to return signals
// }
// each `cycle` will call a callback to triger difftest
// in difftest, get_signals be implemented with VPI
// each `cycle`, VCS use DPI to get data
//
//
// void difftest() {
//   SomeStruct some_struct;
//   get_singals(&some_struct);
//   // do difftest
// }

class VBridge {
public:
  VBridge();

  ~VBridge();

  void setup(const std::string &bin, const std::string &vcd, uint64_t reset_vector, uint64_t cycles) const;

  void loop() const;

  void configure_simulator(int argc, char **argv) const;

private:
  std::unique_ptr<VBridgeImpl> impl;
};
