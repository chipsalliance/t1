#ifdef COSIM_VERILATOR

#include <VTestBench__Dpi.h>

#endif

#include <csignal>

#include <fmt/core.h>
#include <glog/logging.h>

#include "svdpi.h"
#include "testharness.h"

#if VM_TRACE

void TestHarness::dpiDumpWave() {
  ::dpiDumpWave((wave + op + rmstring + ".fst").c_str());
}

#endif

void dpiInitCosim() {
  svSetScope(svGetScopeFromName("TOP.TestBench.verificationModule.verbatim"));
  testharness_instance.dpiInitCosim();
}

void dpiReport() {
  testharness_instance.report();
}

void dpiPeek(svBit ready) {
  testharness_instance.dpiPeek(ready);
}

void dpiPoke(
                 svBit *valid,
                 svBitVecVal *a,
                 svBitVecVal *b,
                 svBit *op,
                 svBitVecVal *rm) {
  testharness_instance.dpiPoke(DutInterface{valid, a, b, op, rm});
}

void dpiCheck(
            svBit valid,
            const svBitVecVal *result,
            const svBitVecVal *fflags) {
   testharness_instance.dpiCheck(valid, *result, *fflags);
}
