#include <VTestBench.h>
#include <VTestBench__Dpi.h>

class VTestBench;

static VerilatedContext *contextp;
static VTestBench *topp;

extern "C" int verilator_main_c(int argc, char **argv) {
  // Setup context, defaults, and parse command line
  Verilated::debug(0);
  contextp = new VerilatedContext();
  contextp->fatalOnError(false);
  contextp->commandArgs(argc, argv);
#ifdef VM_TRACE
  contextp->traceEverOn(true);
#endif

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

#ifdef VM_TRACE
extern "C" void dump_wave_c(char *path) {
  svSetScope(svGetScopeFromName("TOP.TestBench.clockGen"));
  dump_wave(path);
}
#endif

extern "C" uint64_t get_t_c() {
  if (contextp) {
    return contextp->time();
  } else { // before ctx is initialized
    return 0;
  }
}
