#include <args.hxx>
#include <glog/logging.h>

#include "vbridge.h"
#include "exceptions.h"
#include "glog_exception_safe.h"

int main(int argc, char **argv) {
  google::InitGoogleLogging(argv[0]);
  google::InstallFailureSignalHandler();

  args::ArgumentParser parser("Vector");
  args::ValueFlag<std::string> bin(parser, "bin", "test case path.", {"bin"});
  args::ValueFlag<std::string> wave(parser, "wave", "wave output path(in fst).", {"wave"});
  args::ValueFlag<uint64_t> reset_vector(parser, "reset_vector", "set reset vector", {"reset-vector"}, 0x1000);
  args::ValueFlag<uint64_t> cycles(parser, "cycles", "set simulation cycles", {"cycles"}, 0x7fffffff);
  parser.ParseCLI(argc, argv);

  try {
    VBridge vb;
    vb.configure_simulator(argc, argv);
    vb.setup(bin.Get(), wave.Get() + ".fst", reset_vector.Get(), cycles.Get());
    vb.loop();
  } catch (TimeoutException &e) {
    return 0;
  } catch (google::CheckFailedException &e) {
    return 1;
  }
}
