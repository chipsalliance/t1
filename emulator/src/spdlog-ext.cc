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
  auto no_log = getenv_or("EMULATOR_NO_LOG", "false");
  do_logging = (no_log == "false");

  // Both the file and console logger are async, here we create a new write buffer with 8192 size in one thread
  spdlog::init_thread_pool(8192, 1);

  // Initialize the file logger to write log into $EMULATOR_LOG_PATH, with each line a JSON log
  std::string log_path = getenv_or("EMULATOR_LOG_PATH", "emulator-log.json");
  auto file_sink = std::make_shared<spdlog::sinks::basic_file_sink_mt>(
      log_path, /*truncate=*/true);
  file_sink->set_pattern("%v");
  file = std::make_shared<spdlog::async_logger>(
      "File", file_sink, spdlog::thread_pool(),
      spdlog::async_overflow_policy::block);
  file->set_error_handler([&](const std::string &msg) {
    throw std::runtime_error(fmt::format("Emulator internal error: {}", msg));
  });

  // Initialize the console logger to output human readable json log
  auto console_sink = std::make_shared<ConsoleSink>();
  console_sink->set_pattern("---\n%v");
  // Combine the console sink and file sink into one logger.
  console = std::make_shared<spdlog::async_logger>(
      "Console", console_sink, spdlog::thread_pool(),
      spdlog::async_overflow_policy::block);
  console->set_error_handler([&](const std::string &msg) {
    throw std::runtime_error(fmt::format("Emulator logger internal error: {}", msg));
  });

  spdlog::register_logger(file);
  spdlog::register_logger(console);
}

uint64_t JsonLogger::get_cycle() { return vbridge_impl_instance.get_t(); }
