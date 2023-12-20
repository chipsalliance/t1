#pragma once

#include <iostream>
#include <optional>
#include <set>

#include <spdlog/async.h>
#include <spdlog/common.h>
#include <spdlog/sinks/basic_file_sink.h>
#include <spdlog/sinks/stdout_sinks.h>
#include <spdlog/spdlog.h>

#include <nlohmann/json.hpp>
using json = nlohmann::json;

/**
 * Get environment variable by the given `env` key or fallback to a default
 * value.
 */
inline std::string getenv_or(const char *env, std::string fallback) {
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
inline std::set<std::string> get_set_from_env(const char *env,
                                              const char delimiter) {
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
  std::set<std::string> whitelist;
  bool enable_sink;

  inline bool is_expected_module(std::string &module) {
    return whitelist.empty() || whitelist.find(module) != whitelist.end();
  }

public:
  explicit ConsoleSink(bool enable): enable_sink(enable) {
    whitelist = get_set_from_env("EMULATOR_WHITELIST_MODULE", ',');
  }

protected:
  void sink_it_(const spdlog::details::log_msg &msg) override {
    if (!enable_sink) {
      return;
    };

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
            fmt::format("Fail to get field name from: {}", data));
      }

      if (!is_expected_module(module_name)) {
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

enum class LogType {
  Info,
  Warn,
  Trace,
  Fatal,
};

class JsonLogger {
private:
  json internal;
  bool do_logging;

  std::shared_ptr<spdlog::async_logger> file;
  std::shared_ptr<spdlog::async_logger> console;

  inline std::string dump(int indent) {
    std::string ret;
    try {
      ret = internal.dump(indent);
    } catch (json::type_error &ex) {
      throw std::runtime_error(fmt::format("fail to dump internal json into string: ", ex.what()));
    }
    return ret;
  }

  uint64_t get_cycle();

  // We can only implement a class method with template inside the class
  // declaration
  template <typename... Arg>
  inline void try_log(LogType log_type,
                      std::optional<fmt::format_string<Arg...>> fmt,
                      Arg &&...args) {
    if (!do_logging)
      return;

    if (fmt.has_value()) {
      auto msg = fmt::format(fmt.value(), args...);
      internal["message"] = msg;
    }

    internal["cycle"] = 1;

    switch (log_type) {
    case LogType::Info:
      file->info("{}", this->dump(-1));
      if (console) console->info("{}", this->dump(2));
      break;
    case LogType::Warn:
      file->warn("{}", this->dump(-1));
      if (console) console->warn("{}", this->dump(2));
      break;
    case LogType::Trace:
      file->trace("{}", this->dump(-1));
      if (console) console->trace("{}", this->dump(2));
      break;
    case LogType::Fatal:
      file->critical("{}", this->dump(-1));
      if (console) console->critical("{}", this->dump(2));
      spdlog::shutdown();

      throw std::runtime_error(internal["message"]);
    }
  }

public:
  JsonLogger();
  ~JsonLogger() = default;

  inline JsonLogger operator()(const char *n) {
    if (!do_logging)
      return *this;

    internal.clear();
    internal["name"] = n;
    return *this;
  }

  template <typename T> inline JsonLogger &with(const char *key, T value) {
    if (!do_logging)
      return *this;

    internal["data"][key] = value;
    return *this;
  }

  // Overload the index operator
  json &operator[](const char *key) { return internal["info"][key]; };

  inline void info() { try_log(LogType::Info, std::nullopt); }
  inline void trace() { try_log(LogType::Trace, std::nullopt); }

  template <typename... Arg>
  inline void info(fmt::format_string<Arg...> fmt, Arg &&...args) {
    try_log(LogType::Info, fmt, args...);
  }

  template <typename... Arg>
  inline void warn(fmt::format_string<Arg...> fmt, Arg &&...args) {
    try_log(LogType::Warn, fmt, args...);
  }

  template <typename... Arg>
  inline void trace(fmt::format_string<Arg...> fmt, Arg &&...args) {
    try_log(LogType::Trace, fmt, args...);
  };

  template <typename... Arg>
  inline void fatal(fmt::format_string<Arg...> fmt, Arg &&...args) {
    try_log(LogType::Fatal, fmt, args...);
  };
};

// Exported symbols
#define FATAL(context)                                                         \
  auto _fatal_fmt_msg = fmt::format("{}", context);                            \
  spdlog::critical("{}", context);                                             \
  spdlog::shutdown();                                                          \
  throw std::runtime_error(_fatal_fmt_msg);

#define CHECK(cond, context)                                                   \
  if (!(cond)) {                                                               \
    auto _f_msg =                                                              \
        fmt::format("check failed: {} : Assertion ({}) failed at {}:{}",       \
                    context, #cond, __FILE__, __LINE__);                       \
    json _j;                                                                   \
    _j["message"] = _f_msg;                                                    \
    FATAL(_j.dump());                                                          \
  }

#define CHECK_EQ(val1, val2, context) CHECK(val1 == val2, context)
#define CHECK_NE(val1, val2, context) CHECK(val1 != val2, context)
#define CHECK_LE(val1, val2, context) CHECK(val1 <= val2, context)
#define CHECK_LT(val1, val2, context) CHECK(val1 < val2, context)
#define CHECK_GE(val1, val2, context) CHECK(val1 >= val2, context)
#define CHECK_GT(val1, val2, context) CHECK(val1 > val2, context)

extern JsonLogger Log;
