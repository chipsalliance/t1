#include "vbridge.h"

#include "mmu.h"
#include "processor.h"
#include "isa_parser.h"
#include "simif.h"
#include <args.hxx>
#include <verilated.h>

#include "simple_sim.h"

int main(int argc, char **argv) {
  args::ArgumentParser parser("Vector");
  args::ValueFlag<std::string> bin(parser, "bin", "test case path.", {"bin"});
  args::ValueFlag<std::string> wave(parser, "wave", "wave output path(in fst).", {"wave"});
  args::ValueFlag<uint64_t> reset_vector(parser, "reset_vector", "set reset vector", {"reset-vector"}, 0x1000);
  args::ValueFlag<uint64_t> cycles(parser, "cycles", "set simulation cycles", {"cycles"}, 0x7fffffff);
  parser.ParseCLI(argc, argv);
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
  auto &ctx = vb.get_verilator_ctx();
  ctx.commandArgs(argc, argv);
  vb.setup(bin.Get(), wave.Get() + ".fst", reset_vector.Get(), cycles.Get());
  vb.loop();

  assert(proc.get_mmu() != nullptr);
}