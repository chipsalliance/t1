#include <VTestBench.h>
#include <VTestBench__Dpi.h>

class VTestBench;

extern "C" int verilator_main_c(int argc, char **argv) {
  // Setup context, defaults, and parse command line
  Verilated::debug(0);

  // use unique_ptr to ensure deletion of context and model
  std::unique_ptr<VerilatedContext> contextp;
  std::unique_ptr<VTestBench> topp;

  // Construct the Verilated context
  contextp = std::make_unique<VerilatedContext>();
  // Construct the Verilated model, from Vtop.h generated from Verilating
  topp = std::make_unique<VTestBench>(contextp.get());

  contextp->fatalOnError(false);
  contextp->commandArgs(argc, argv);
#ifdef VM_TRACE
  contextp->traceEverOn(true);
#endif

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
