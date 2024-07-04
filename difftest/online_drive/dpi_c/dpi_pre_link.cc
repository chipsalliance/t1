#include <VTestBench.h>
#include <VTestBench__Dpi.h>

#include "dpi_pre_link.h"

class VTestBench;

int verilator_main(int argc, char **argv) {
  // Setup context, defaults, and parse command line
  Verilated::debug(0);
  const std::unique_ptr<VerilatedContext> contextp{new VerilatedContext};
  contextp->commandArgs(argc, argv);

  // Construct the Verilated model, from Vtop.h generated from Verilating
  const std::unique_ptr<VTestBench> topp{new VTestBench{contextp.get()}};

  // Simulate until $finish
  while (!contextp->gotFinish()) {
    // Evaluate model
    topp->eval();
    // Advance time
    if (!topp->eventsPending())
      break;
    contextp->time(topp->nextTimeSlot());
  }

  if (!contextp->gotFinish()) {
    VL_DEBUG_IF(VL_PRINTF("+ Exiting without $finish; no events left\n"););
  }

  // Final model cleanup
  topp->final();
  return 0;
}

void dump_wave(char *path) {
    svSetScope(svGetScopeFromName("TOP.TestBench.DumpWave"));
    DumpWave(path);
}

void init_wave() {
  Verilated::traceEverOn(true);
}
