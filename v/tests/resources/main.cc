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
  args::ValueFlag<std::string> vcd(parser, "vcd", "vcd output path.", {"vcd"});
  args::ValueFlag<int> reset_vector(parser, "reset_vector", "set reset vector", {"reset-vector"}, 0x1000);
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
  vb.setup(bin.Get(), vcd.Get(), reset_vector.Get());
  auto &ctx = vb.get_verilator_ctx();
  ctx.commandArgs(argc, argv);
  vb.loop();

  assert(proc.get_mmu() != nullptr);
}