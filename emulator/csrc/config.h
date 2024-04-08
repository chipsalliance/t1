#pragma once

#include <condition_variable>
#include <list>
#include <mutex>
#include <optional>
#include <queue>
#include <thread>
#include <utility>
#include <string>
#include <args.hxx>
#include "spdlog_ext.h"


class Config {
  public:
  explicit Config() {
    std::ifstream cmdline("/proc/self/cmdline");
    std::vector<std::string> arg_vec;
    for (std::string line; std::getline(cmdline, line, '\0');) {
      arg_vec.emplace_back(line);
    }
    std::vector<const char *> argv;
    argv.reserve(arg_vec.size());
    for (const auto &arg: arg_vec) {
      argv.emplace_back(arg.c_str());
    }

    args::ArgumentParser parser("emulator for t1");

    args::Flag no_logging(parser, "no_logging", "Disable all logging utilities.", {"no-logging"});
    args::Flag no_file_logging(parser, "no_file_logging", "Disable file logging utilities.", {"no-file-logging"});
    args::Flag no_console_logging(parser, "no_console_logging", "Disable console logging utilities.",
                                  {"no-console-logging"});
    args::ValueFlag<std::string> log_path(parser, "log path", "Path to store logging file", {"log-path"});

    args::ValueFlag<size_t> vlen(parser, "vlen", "match from RTL config, tobe removed", {"vlen"},
                                 args::Options::Required);
    args::ValueFlag<size_t> dlen(parser, "dlen", "match from RTL config, tobe removed", {"dlen"},
                                 args::Options::Required);
    args::ValueFlag<size_t> tl_bank_number(parser, "tl_bank_number", "match from RTL config, tobe removed",
                                           {"tl_bank_number"}, args::Options::Required);
    args::ValueFlag<size_t> beat_byte(parser, "beat_byte", "match from RTL config, tobe removed", {"beat_byte"},
                                      args::Options::Required);

    args::ValueFlag<std::string> bin_path(parser, "elf path", "", {"elf"}, args::Options::Required);
    args::ValueFlag<std::string> wave_path(parser, "wave path", "", {"wave"}, args::Options::Required);
    args::ValueFlag<std::optional<std::string>> perf_path(parser, "perf path", "", {"perf"});
    args::ValueFlag<uint64_t> timeout(parser, "timeout", "", {"timeout"}, args::Options::Required);
#if VM_TRACE
    args::ValueFlag<uint64_t> dump_from_cycle(parser, "dump_from_cycle", "start to dump wave at cycle", {"dump-from-cycle"}, args::Options::Required);
#endif
    args::ValueFlag<double> tck(parser, "tck", "", {"tck"}, args::Options::Required);
    args::Group dramsim_group(parser, "dramsim config", args::Group::Validators::AllOrNone);
    args::ValueFlag<std::optional<std::string>> dramsim3_config_path(dramsim_group, "config path", "",
                                                                     {"dramsim3-config"});
    args::ValueFlag<std::optional<std::string>> dramsim3_result_dir(dramsim_group, "result dir", "",
                                                                    {"dramsim-result"});

    try {
      parser.ParseCLI((int) argv.size(), argv.data());
    } catch (args::Help &) {
      std::cerr << parser;
      std::exit(0);
    } catch (args::Error &e) {
      std::cerr << e.what() << std::endl << parser;
      std::exit(1);
    }
    Log = JsonLogger(no_logging.Get(), no_file_logging.Get(), no_console_logging.Get(), log_path.Get());

    Config(
        bin_path.Get(),
        wave_path.Get(),
        perf_path.Get(),
        timeout.Get(),
#if VM_TRACE
        dump_from_cycle(dump_from_cycle.Get(),
#endif
        tck.Get(),
        dramsim3_config_path.Get(),
        dramsim3_result_dir.Get(),
        vlen.Get(),
        dlen.Get(),
        tl_bank_number.Get(),
        // TODO: clean me up
        32,
        dlen.Get() / 32,
        32,
        32,
        3,
        255,
        vlen.Get() / 8,
        beat_byte.Get()
    );
  }

  public:
  Config(
      std::string binPath,
      std::string wavePath,
      const std::optional<std::string> perfPath,
      uint64_t timeout,
#if VM_TRACE
      uint64_t dumpFromCycle,
#endif
      double tck,
      const std::optional<std::string> &dramsim3ConfigPath,
      const std::optional<std::string> &dramsim3ResultDir,
      size_t vlen,
      size_t dlen,
      size_t tlBankNumber,
      size_t datapathWidth,
      size_t laneNumber,
      size_t elen,
      size_t vregNumber,
      size_t mshrNumber,
      size_t lsuIdxDefault,
      size_t vlenInBytes,
      size_t datapathWidthInBytes
  )
      :
      bin_path(std::move(binPath)),
      wave_path(std::move(wavePath)),
      perf_path(perfPath),
      timeout(timeout),
#if VM_TRACE
      dump_from_cycle(dumpFromCycle),
#endif
      tck(tck),
      dramsim3_config_path(dramsim3ConfigPath),
      dramsim3_result_dir(dramsim3ResultDir),
      vlen(vlen),
      dlen(dlen),
      tl_bank_number(tlBankNumber),
      datapath_width(datapathWidth),
      lane_number(laneNumber),
      elen(elen),
      vreg_number(vregNumber),
      mshr_number(mshrNumber),
      lsu_idx_default(lsuIdxDefault),
      vlen_in_bytes(vlenInBytes),
      datapath_width_in_bytes(datapathWidthInBytes) {};

  std::string bin_path;
  std::string wave_path;
  std::optional<std::string> perf_path;
  uint64_t timeout{};

#ifdef VM_TRACE
  uint64_t dump_from_cycle{};
#endif

  double tck{};
  std::optional<std::string> dramsim3_config_path;
  std::optional<std::string> dramsim3_result_dir;
  // TODO: move these configs to compiler time after t1 support OM:
  // TODO: these are unused parameters
  size_t vlen{};
  size_t dlen{};
  size_t tl_bank_number{};

  size_t datapath_width{};
  size_t lane_number{};
  size_t elen{};
  size_t vreg_number{};
  size_t mshr_number{};
  size_t lsu_idx_default{};
  size_t vlen_in_bytes{};
  size_t datapath_width_in_bytes{};
};
