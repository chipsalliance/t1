#include <filesystem>
#include <fmt/core.h>
#include <fstream>
#include <iostream>
#include <memory>
#include <mutex>
#include <set>
#include <sstream>
#include <string>

#include "spdlog-ext.h"
#include "vbridge_impl.h"

// Constructor
JsonLogger::JsonLogger() {
  do_logging = getenv_or("EMULATOR_no_log", "false") == "false";

  // Both the file and console logger are async, here we create a new write buffer with 8192 size in one thread
  spdlog::init_thread_pool(8192, 1);

  // Initialize the file logger to write log into $EMULATOR_LOG_PATH, with each line a JSON log
  std::string log_path = getenv_or("EMULATOR_log_path", "emulator-log.json");
  auto file_sink = std::make_shared<spdlog::sinks::basic_file_sink_mt>(
      log_path, /*truncate=*/true);
  file_sink->set_pattern("%v");
  file = std::make_shared<spdlog::async_logger>(
      "File", file_sink, spdlog::thread_pool(),
      spdlog::async_overflow_policy::block);
  file->set_error_handler([](const std::string &msg) {
    throw std::runtime_error(fmt::format("Emulator internal error: {}", msg));
  });
  file->flush_on(spdlog::level::critical);

  bool do_console_log = getenv_or("EMULATOR_no_console_log", "false") == "false";
  if (do_console_log) {
    // Initialize the console logger to output human-readable json log
    auto console_sink = std::make_shared<ConsoleSink>(do_console_log);
    console_sink->set_pattern("---\n%v");
    // Combine the console sink and file sink into one logger.
    console = std::make_shared<spdlog::async_logger>(
        "Console", console_sink, spdlog::thread_pool(),
        spdlog::async_overflow_policy::block);
    console->set_error_handler([&](const std::string &msg) {
      throw std::runtime_error(fmt::format("Emulator logger internal error: {}", msg));
    });
    spdlog::register_logger(console);
  }

  spdlog::register_logger(file);
  spdlog::set_default_logger(file);
}

uint64_t JsonLogger::get_cycle() { return vbridge_impl_instance.get_t(); }

JsonLogger Log;
