#include <memory>
#include <mutex>
#include <string>

#include <fmt/core.h>

#include "spdlog-ext.h"
#include "vbridge_impl.h"

/**
 * Get environment variable by the given `env_namae` key or fallback to a default
 * value.
 */
static std::string getenv_or(const char *env_namae, std::string fallback) {
  char *env_var = std::getenv(env_namae);
  if (env_var && strlen(env_var) > 0) {
    return std::string{env_var};
  }

  return fallback;
}

static spdlog::level::level_enum get_level_from_env(const char *env_name, spdlog::level::level_enum fallback) {
  char *env_var = std::getenv(env_name);
  if (env_var == nullptr) {
    return fallback;
  } else {
    std::string env_var_string(env_var);
    if (env_var_string == "TRACE") {
      return spdlog::level::trace;
    } else if (env_var_string == "INFO") {
      return spdlog::level::info;
    } else if (env_var_string == "WARN") {
      return spdlog::level::warn;
    } else if (env_var_string == "FATAL") {
      return spdlog::level::critical;
    } else {
      throw std::runtime_error(fmt::format("unknown log level {} from env {}", env_var, env_name));
    }
  }
}

static std::set<std::string> get_set_from_env(const char *env_name, const char delimiter) {
  std::set<std::string> set;

  auto raw = getenv_or(env_name, "");
  if (raw.empty()) return set;

  std::stringstream ss(raw);
  std::string token;
  // Use `delimiter` instead of '\n' to split element in string.
  while (std::getline(ss, token, delimiter))
    set.insert(token);

  return set;
}

ConsoleSink::ConsoleSink(bool enable) : enable_sink(enable) {
  whitelist = get_set_from_env("EMULATOR_WHITELIST_MODULE", ',');
  whitelist.insert("DPIInitCosim");
  whitelist.insert("SpikeStep");
  whitelist.insert("SimulationExit");
  whitelist.insert("DPIPeekIssue");
  whitelist.insert("DPIPokeInst");
}

inline bool ConsoleSink::is_module_enabled(std::string &module) {
  return whitelist.empty() || whitelist.find(module) != whitelist.end();
}

void ConsoleSink::sink_it_(const spdlog::details::log_msg &msg) {
  if (!enable_sink) {
    return;
  };

  auto data = std::string(msg.payload.data(), msg.payload.size());
  // Don't touch error message
  if (msg.level == spdlog::level::info) {
    std::string module_name;
    try {
      json payload = json::parse(data);
      module_name = payload["_module"];
    } catch (const json::parse_error &ex) {
      throw std::runtime_error(
        fmt::format("Failed to convert msg {} to json: {}", data, ex.what()));
    } catch (const json::type_error &ex) {
      throw std::runtime_error(
        fmt::format("Failed to get field ‘name’ from: {}", data));
    }

    if (!is_module_enabled(module_name)) {
      return;
    }
  }

  spdlog::memory_buf_t formatted;
  spdlog::sinks::base_sink<std::mutex>::formatter_->format(msg, formatted);
// stdout will be captured by mill, so we need to print them into stderr
  std::cerr << fmt::to_string(formatted);
}

void ConsoleSink::flush_() {
  std::cerr << std::flush;
}

// Constructor
JsonLogger::JsonLogger(bool no_logging, bool no_file_logging, bool no_console_logging,
                       const std::optional<std::string> &log_path) : do_logging(!no_logging) {
  if (no_console_logging && no_file_logging) do_logging = false;

  // Both the file and console logger are async, here we create a new write buffer with 8192 size in one thread
  spdlog::init_thread_pool(8192, 1);

  // Initialize the file logger to write log into $EMULATOR_LOG_PATH, with each line a JSON log
  if (do_logging && !no_file_logging) {
    auto file_sink = std::make_shared<spdlog::sinks::basic_file_sink_mt>(
      log_path.value_or("emulator_log"), /*truncate=*/true);
    file_sink->set_pattern("%v");
    file = std::make_shared<spdlog::async_logger>(
      "File", file_sink, spdlog::thread_pool(),
      spdlog::async_overflow_policy::block);
    file->set_error_handler([](const std::string &msg) {
      throw std::runtime_error(fmt::format("Emulator internal error: {}", msg));
    });
    file->set_level(get_level_from_env("EMULATOR_FILE_LOG_LEVEL", spdlog::level::info));
    file->flush_on(spdlog::level::critical);

    spdlog::register_logger(file);
    spdlog::set_default_logger(file);
  }

  if (do_logging && !no_console_logging) {
    // Initialize the console logger to output human-readable json log
    auto console_sink = std::make_shared<ConsoleSink>(do_logging);
    console_sink->set_pattern("%v");
    // Combine the console sink and file sink into one logger.
    console = std::make_shared<spdlog::async_logger>(
      "Console", console_sink, spdlog::thread_pool(),
      spdlog::async_overflow_policy::block);
    console->set_error_handler([&](const std::string &msg) {
      throw std::runtime_error(fmt::format("Emulator logger internal error: {}", msg));
    });
    file->set_level(get_level_from_env("EMULATOR_CONSOLE_LOG_LEVEL", spdlog::level::info));
    spdlog::register_logger(console);
  }
}

// We can only implement a class method with template inside the class
// declaration
void JsonLogger::LogBuilder::do_log(spdlog::level::level_enum level) {
  logContent["_cycle"] = vbridge_impl_instance.get_t() % 10;
  logContent["_module"] = module_name;

  if (logger->file) {
    logger->file->log(level, logContent.dump(-1));
  }

  if (logger->console) {
    logger->console->log(level, logContent.dump(-1));
  }
}

// Notes: initialization move to vbridege_impl.cc: vbridgeImplFromArgs
JsonLogger Log(true, true, true, std::nullopt);
