#pragma once

#include <optional>
#include <queue>

#include "verilated_fst_c.h"
#include <VTestBench__Dpi.h>

#include <svdpi.h>

#include "util.h"

#include <cassert>
#include <cstdint>
#include <cstdio>

extern "C" {
#include "functions.h"
#include "genCases.h"
#include "genLoops.h"
#include "softfloat.h"
}

struct DutInterface {
  svBit *valid;
  svBitVecVal *a;
  svBitVecVal *b;
  svBit *op;
  svBitVecVal *rm;
};

struct testdata {
  uint64_t a;
  uint64_t b;
  uint64_t expected_out;
  testfloat_function_t function;
  exceptionFlag_t expectedException;
};

class TestHarness {
public:
  explicit TestHarness();

  std::queue<testdata> test_queue;

  testdata testcase;

  void initTestCases();

  void dpiDumpWave();

  void dpiInitCosim();

  void dpiPoke(const DutInterface &toDut);

  void dpiPeek(svBit ready);

  void dpiCheck(svBit valid, svBitVecVal result, svBitVecVal fflags);

  uint64_t getCycle() { return ctx->time(); }

  void setAvailable();

  void clrAvailable();

  void reloadCase();

  int report();

private:
  VerilatedContext *ctx;
  VerilatedFstC tfp;

  uint64_t _cycles;

  bool terminate;

  bool available;

  const std::string wave = get_env_arg("wave");

  const std::string op = get_env_arg("op");

  const int rm = std::stoul(get_env_arg("rm"), nullptr, 10);

  uint64_t cnt;

  roundingMode_t roundingMode;

  bool opSignal;

  std::string rmstring;
};

extern TestHarness testharness_instance;
