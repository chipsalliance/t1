#include <filesystem>
#include <fmt/core.h>
#include <fstream>
#include <iostream>
#include <memory>
#include <mutex>
#include <set>
#include <sstream>
#include <string>

#include <spdlog/async.h>
#include <spdlog/common.h>
#include <spdlog/sinks/basic_file_sink.h>
#include <spdlog/sinks/stdout_sinks.h>
#include <spdlog/spdlog.h>

#include "spdlog-ext.h"

/**
 * Get environment variable by the given `env` key or fallback to a default
 * value.
 */
std::string getenv_or(const char *env, std::string fallback) {
  char *env_var = std::getenv(env);
  if (env_var && strlen(env_var) > 0) {
    return std::string{env_var};
  }

  return fallback;
}

/**
 * Get environment variable and split them into std::vector by the given
 * `delimiter`
 */
std::set<std::string> get_set_from_env(const char *env, const char delimiter) {
  std::set<std::string> set;

  auto raw = getenv_or(env, "");
  if (raw.empty()) {
    return set;
  }

  std::stringstream ss(raw);
  std::string token;
  // Use `delimiter` instead of '\n' to split element in string.
  while (std::getline(ss, token, delimiter)) {
    set.insert(token);
  }

  return set;
}

/**
 * Filter logging message by module type. Filter should be set by environment
 * `EMULATOR_BLACKLIST_MODULE` and `EMULATOR_WHITELIST_MODULE`. Module should be
 * separate by column `,`. When you set both env, only blacklist will have
 * effect.
 */
class ConsoleSink : public spdlog::sinks::base_sink<std::mutex> {
private:
  std::set<std::string> blacklist;
  std::set<std::string> whitelist;

public:
  ConsoleSink()
      : blacklist{get_set_from_env("EMULATOR_BLACKLIST_MODULE", ',')},
        whitelist{get_set_from_env("EMULATOR_WHITELIST_MODULE", ',')} {}

protected:
  void sink_it_(const spdlog::details::log_msg &msg) override {
    auto data = std::string(msg.payload.data(), msg.payload.size());
    // Don't touch error message
    if (msg.level == spdlog::level::info) {
      std::string module_name;
      try {
        json payload = json::parse(data);
        payload["name"].get_to(module_name);
      } catch (const json::parse_error &ex) {
        throw std::runtime_error(
            fmt::format("Fail to convert msg {} to json: {}", data, ex.what()));
      } catch (const json::type_error &ex) {
        throw std::runtime_error(
            fmt::format("Fail to get field name from: {}", msg.payload.data()));
      }

      // If module name was found in blacklist
      if (!blacklist.empty() && !blacklist.find(module_name)->empty()) {
        return;
      }
      // If module name wat not found in whitelist
      if (!whitelist.empty() && whitelist.find(module_name)->empty()) {
        return;
      }
    }

    spdlog::memory_buf_t formatted;
    spdlog::sinks::base_sink<std::mutex>::formatter_->format(msg, formatted);
    // stdout will be captured by mill, so we need to print them into stderr
    std::cerr << fmt::to_string(formatted);
  }

  void flush_() override { std::cerr << std::flush; }
};

/**
 * Create two logging sink, one for creating JSON format logging file, another
 * for more human readable console output. User can use environment variable
 * `EMULATOR_LOG_PATH` to control the logging file path.
 */
void setup_logger() {
  std::string log_path = getenv_or("EMULATOR_LOG_PATH", "emulator-log.json");

  // Pre-process the file so that after program exit, it can be a valid JSON
  // array.
  spdlog::file_event_handlers f_handlers;
  f_handlers.after_open = [](spdlog::filename_t _, std::FILE *fstream) {
    fputs("[\n", fstream);
  };
  f_handlers.after_close = [](spdlog::filename_t log_path) {
    std::ifstream old_log_file(log_path);
    std::ofstream temp_file("temp-log.json");
    std::string line;
    while (std::getline(old_log_file, line)) {
      temp_file << line;
    }
    // Drop the last ','
    temp_file.seekp(-1, std::ios::cur);
    temp_file.write("]\n", 2);
    temp_file.close();
    old_log_file.close();
    std::filesystem::rename("temp-log.json", log_path);
  };
  auto file_sink = std::make_shared<spdlog::sinks::basic_file_sink_mt>(
      log_path, /*truncate=*/true, f_handlers);
  // Basic logging information, other data are nested in the `data` attribute
  // set.
  file_sink->set_pattern(
      "{ \"timestamp\": \"%E\", \"level\": \"%l\", \"data\": %v },");

  auto console_sink = std::make_shared<ConsoleSink>();
  // %T: "23:55:59" (We don't need other information.)
  console_sink->set_pattern("%T %v");

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
