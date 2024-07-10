#include <VTestBench.h>
#include <VTestBench__Dpi.h>

#include "dpi_pre_link.h"

class VTestBench;

VerilatedContext *contextp;
VTestBench *topp;

int verilator_main_c(int argc, char **argv) {
  // Setup context, defaults, and parse command line
  Verilated::debug(0);
  contextp = new VerilatedContext();
  contextp->commandArgs(argc, argv);

  // Construct the Verilated model, from Vtop.h generated from Verilating
  topp = new VTestBench(contextp);

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

  delete topp;
  delete contextp;

  return 0;
}

void dump_wave_c(char *path) {
  Verilated::traceEverOn(true);
  svSetScope(svGetScopeFromName("TOP.TestBench.DumpWave"));
  DumpWave(path);
}

uint64_t get_t_c() {
  if (contextp) {
    return contextp->time();
  } else { // before ctx is initialized
    return 0;
  }
}
