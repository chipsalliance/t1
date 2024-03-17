#include <memory>
#include <mutex>
#include <string>

#include <fmt/core.h>
#include <fmt/color.h>

#include "spdlog_ext.h"
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

ConsoleSink::ConsoleSink() {
  whitelist = get_set_from_env("EMULATOR_LOG_WHITELIST", ',');
  whitelist.insert("DPIInitCosim");
  whitelist.insert("SpikeStep");
  whitelist.insert("SimulationExit");
  whitelist.insert("DPIPeekIssue");
  whitelist.insert("DPIPokeInst");

  // putting it in JsonLogger::JsonLogger will not work. not knowing why
  this->set_level(get_level_from_env("EMULATOR_CONSOLE_LOG_LEVEL", spdlog::level::info));
}

inline bool ConsoleSink::is_module_enabled(const std::string &module) {
  return whitelist.empty() || whitelist.find(module) != whitelist.end();
}

void ConsoleSink::sink_it_(const spdlog::details::log_msg &msg) {
  json payload = json::parse(msg.payload);

  // filter message matching the current level
  if (msg.level == this->level()) {
    if (!is_module_enabled(payload["_module"])) return;
  }

  fmt::text_style level_color;
  switch (msg.level) {
  case spdlog::level::debug:
  case spdlog::level::trace:
    level_color = fmt::fg(fmt::color::gray);
    break;
  case spdlog::level::info:
    level_color = fmt::fg(fmt::color::white);
    break;
  case spdlog::level::warn:
    level_color = fmt::fg(fmt::color::yellow);
    break;
  case spdlog::level::err:
    level_color = fmt::fg(fmt::color::red);
      break;
  case spdlog::level::critical:
    level_color = fmt::bg(fmt::color::red) | fmt::fg(fmt::color::white);
    break;
  default:
    level_color = fmt::fg(fmt::color::white);
    break;
  }

  std::cerr << fmt::format("{} {}",
                           fmt::styled(payload["_cycle"].get<int64_t>(), level_color),
                           fmt::styled(payload["_module"].get<std::string>(), fmt::fg(fmt::color::violet))
                           );
  if (payload.contains("_msg")) {
    std::cerr << fmt::format(" {}", fmt::styled(payload["_msg"].get<std::string>(), fmt::fg(fmt::color::green)));
  }
  if (payload.contains("_with")) {
    std::cerr << fmt::format(" {}", fmt::styled(payload["_with"].dump(), fmt::fg(fmt::color::gray)));
  }
  std::cerr << "\n";
}

void ConsoleSink::flush_() {
  std::cerr << std::flush;
}

// Constructor

JsonLogger::JsonLogger(bool no_logging, bool no_file_logging, bool no_console_logging,
                       const std::string &log_path) : do_logging(!no_logging) {
  if (no_console_logging && no_file_logging) do_logging = false;

  // Both the file and console logger are async, here we create a new write buffer with 8192 size in one thread
  spdlog::init_thread_pool(8192, 1);

  if (do_logging && !no_file_logging) {
    auto file_sink = std::make_shared<spdlog::sinks::basic_file_sink_mt>(log_path, /*truncate=*/true);
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
    auto console_sink = std::make_shared<ConsoleSink>();
    console_sink->set_pattern("%v");
    // Combine the console sink and file sink into one logger.
    console = std::make_shared<spdlog::async_logger>(
      "Console", console_sink, spdlog::thread_pool(),
      spdlog::async_overflow_policy::block);
    console->set_error_handler([&](const std::string &msg) {
      throw std::runtime_error(fmt::format("Emulator logger internal error: {}", msg));
    });
    spdlog::register_logger(console);
  }
}

JsonLogger::JsonLogger(): do_logging(false) { }

// We can only implement a class method with template inside the class
// declaration
void JsonLogger::LogBuilder::do_log(spdlog::level::level_enum level) {
  logContent["_cycle"] = vbridge_impl_instance.get_t() / 10;
  logContent["_module"] = module_name;

  if (logger->file) logger->file->log(level, logContent.dump());
  if (logger->console) logger->console->log(level, logContent.dump());
}

// Notes: initialization move to vbridege_impl.cc: vbridgeImplFromArgs
JsonLogger Log;
