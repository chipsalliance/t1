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

/**
 * Create two logging sink, one for creating JSON format logging file, another
 * for more human readable console output. User can use environment variable
 * `EMULATOR_LOG_PATH` to control the logging file path.
 */
void setup_logger() {
  std::string log_path = getenv_or("EMULATOR_LOG_PATH", "emulator-log.json");

  auto file_sink = std::make_shared<spdlog::sinks::basic_file_sink_mt>(log_path, /*truncate=*/true);
  file_sink->set_pattern("%v");

  auto console_sink = std::make_shared<ConsoleSink>();
  console_sink->set_pattern("- %v");

  // One thread with 8192 queue size
  spdlog::init_thread_pool(8192, 1);
  std::vector<spdlog::sink_ptr> sinks;
  sinks.push_back(file_sink);
  sinks.push_back(console_sink);

  // Combine the console sink and file sink into one logger.
  auto logger = std::make_shared<spdlog::async_logger>(
      "Emulator", std::begin(sinks), std::end(sinks), spdlog::thread_pool(),
      spdlog::async_overflow_policy::block);
  logger->set_error_handler([&](const std::string &msg) {
    throw std::runtime_error(fmt::format("Emulator internal error: {}", msg));
  });
  logger->flush_on(spdlog::level::info);
  logger->flush_on(spdlog::level::err);
  logger->flush_on(spdlog::level::critical);

  spdlog::set_default_logger(logger);

  return;
}

uint64_t JsonLogger::get_cycle() {
  return vbridge_impl_instance.get_t();
}
