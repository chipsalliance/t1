#include <filesystem>
#include <fstream>

#include <fmt/core.h>
#include <fmt/ranges.h>
#include <args.hxx>

#include <verilated.h>

#include "exceptions.h"
#include "spdlog_ext.h"
#include "vbridge_impl.h"

void VBridgeImpl::dpiInitCosim(uint32_t *resetVector) {
  ctx = Verilated::threadContextp();

  uint32_t elf_entry = unified_mem.load_elf(0x0, bin.c_str());
  *resetVector = elf_entry;

  Log("DPIInitCosim")
    .with("bin", bin)
    .with("wave", wave)
    .with("timeout", timeout)
    .info("Simulation environment initialized");

#if VM_TRACE
  dpiDumpWave();
#endif
}

//==================
// posedge (1)
//==================

void VBridgeImpl::PeekVectorTL(uint8_t channel, VTlInterfacePeek &peek) {
  resizeVectorChannel(channel);
  vector_sigs[channel].update_input_dpi(peek);
  tilelink_ref<32, 8, 15, 3> ref = vector_sigs[channel];
  vector_xbar[channel].tick(ref);
}

void VBridgeImpl::PeekMMIOTL(VTlInterfacePeek &peek) {
  mmio_sigs.update_input_dpi(peek);
  tilelink_ref <29, 8, 2, 3> ref = mmio_sigs;
  mmio_xbar.tick(ref);
  // output uart
  while (uart.exist_tx()) {
    char c = uart.getc();
    if (c == EOF) {
      finished = true;
    }
    printf("%c",c);
    fflush(stdout);
  }
}

void VBridgeImpl::PeekScarlarTL(VTlInterfacePeek &peek) {
  mem_sigs.update_input_dpi(peek);
  tilelink_ref <30, 8, 16, 3> ref = mem_sigs;
  mem_xbar.tick(ref);
}

//==================
// posedge (2)
//==================

void VBridgeImpl::PokeVectorTL(uint8_t channel, VTlInterfacePoke &poke) {
  resizeVectorChannel(channel);
  vector_sigs[channel].update_output_dpi(poke);
}

void VBridgeImpl::PokeMMIOTL(VTlInterfacePoke &poke) {
  mmio_sigs.update_output_dpi(poke);
}

void VBridgeImpl::PokeScarlarTL(VTlInterfacePoke &poke) {
  mem_sigs.update_output_dpi(poke);
}

//==================
// end of dpi interfaces
//==================

static VBridgeImpl vbridgeImplFromArgs() {
  std::ifstream cmdline("/proc/self/cmdline");
  std::vector<std::string> arg_vec;
  for (std::string line; std::getline(cmdline, line, '\0');) {
    arg_vec.emplace_back(line);
  }
  std::vector<const char*> argv;
  argv.reserve(arg_vec.size());
  for (const auto& arg: arg_vec) {
    argv.emplace_back(arg.c_str());
  }

  args::ArgumentParser parser("subsystem emulator for t1");

  args::Flag no_logging(parser, "no_logging", "Disable all logging utilities.", { "no-logging" });
  args::Flag no_file_logging(parser, "no_file_logging", "Disable file logging utilities.", { "no-file-logging" });
  args::Flag no_console_logging(parser, "no_console_logging", "Disable console logging utilities.", { "no-console-logging" });
  args::ValueFlag<std::optional<std::string>> log_path(parser, "log path", "Path to store logging file", {"log-path"});

  args::ValueFlag<std::string> bin_path(parser, "elf path", "", {"elf"}, args::Options::Required);
#ifdef VM_TRACE
  args::ValueFlag<std::string> wave_path(parser, "wave path", "", {"wave"}, args::Options::Required);
#endif
  args::ValueFlag<uint64_t> timeout(parser, "timeout", "", {"timeout"}, args::Options::Required);

  try {
    parser.ParseCLI((int) argv.size(), argv.data());
  } catch (args::Help&) {
    std::cerr << parser;
    std::exit(0);
  } catch (args::Error& e) {
    std::cerr << e.what() << std::endl << parser;
    std::exit(1);
  }

  Log = JsonLogger(no_logging.Get(), no_file_logging.Get(), no_console_logging.Get(), log_path.Get().value_or("soc-emulator-log.txt"));

  Config cosim_config {
    .bin_path = bin_path.Get(),
    .wave_path = wave_path.Get(),
    .timeout = timeout.Get()
  };

  return VBridgeImpl(cosim_config);
}

VBridgeImpl::VBridgeImpl(const Config cosim_config)
    : config(cosim_config),
      bin(config.bin_path),
      wave(config.wave_path),
      timeout(config.timeout),
      finished(false),
      unified_mem(4096ul*1024*1024),
#ifdef COSIM_VERILATOR
      ctx(nullptr)
#endif
{
  mmio_xbar.add_dev(0x10000000, 32, &uart);
  bool canAddMem = mem_xbar.add_dev(0, 4096ul*1024*1024, &unified_mem);
  if (!canAddMem) {
    Log("VBridgeImpl")
      .info("Can not initialize memory.");
    exit(1);
  }
}

void VBridgeImpl::on_exit() {
  // TODO: output perf summary
}

void VBridgeImpl::resizeVectorChannel (uint8_t channel) {
  while (channel >= vector_sigs.size()) {
    vector_sigs.emplace_back();
    vector_xbar.emplace_back();
    bool res = vector_xbar[channel].add_dev(0, 4096ul*1024*1024, &unified_mem);
    if (!res) printf("Can not initialize memory.\n");
  }
}

VBridgeImpl vbridge_impl_instance = vbridgeImplFromArgs();
