#include "vbridge.h"

#include "mmu.h"
#include "processor.h"
#include "isa_parser.h"
#include "simif.h"

#include "simple_sim.h"

int main(int argc, char **argv) {
  isa_parser_t isa("rv32gcv", DEFAULT_PRIV);
  simple_sim sim(1 << 30);

  processor_t proc(/*isa*/ &isa,
                   /*varch*/ "vlen:128,elen:32",
                   /*sim*/ &sim,
                   /*id*/ 0,
                   /*halt on reset*/ true,
                   /*log_file_t*/ nullptr,
                   /*sout*/ std::cerr);

  VBridge vb(proc, sim);
  vb.setup(argc, argv);
  vb.loop();

  assert(proc.get_mmu() != nullptr);
}