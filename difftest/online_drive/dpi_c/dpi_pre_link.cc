#include <VTestBench.h>
#include <VTestBench__Dpi.h>
#include <memory>

class VTestBench;

static std::unique_ptr<VerilatedContext> contextp;
static std::unique_ptr<VTestBench> topp;

extern "C" int verilator_main_c(int argc, char **argv) {
  // Setup context, defaults, and parse command line
  Verilated::debug(0);
  contextp = std::make_unique<VerilatedContext>();
  contextp->fatalOnError(false);
  contextp->commandArgs(argc, argv);
#ifdef VM_TRACE
  contextp->traceEverOn(true);
#endif

  // Construct the Verilated model, from Vtop.h generated from Verilating
  topp = std::make_unique<VTestBench>(contextp.get());

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
    return 1;
  }

  if (contextp->gotError()) {
    VL_DEBUG_IF(VL_PRINTF("+ Exiting due to errors\n"););
    return 1;
  }

  // Final model cleanup
  topp->final();

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
