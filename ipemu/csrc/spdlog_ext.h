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
 * Filter logging message by module type. Filter should be set by environment
 * `EMULATOR_BLACKLIST_MODULE` and `EMULATOR_WHITELIST_MODULE`. Module should be
 * separate by column `,`. When you set both env, only blacklist will have
 * effect.
 */
class ConsoleSink : public spdlog::sinks::base_sink<std::mutex> {
private:
  std::set<std::string> whitelist;
  bool enable_sink;
  bool is_module_enabled(const std::string &module);

public:
  ConsoleSink();

protected:
  void sink_it_(const spdlog::details::log_msg &msg) override;
  void flush_() override;
};

class JsonLogger {
public:

  // a transient class to build up log message
  class LogBuilder {
  public:
    explicit LogBuilder(JsonLogger *logger, const char *name) : logger(logger), module_name(name) {};

    template<typename T>
    inline LogBuilder &with(const char *key, T value) {
      if (!logger->do_logging) return *this;

      logContent["_with"][key] = value;
      return *this;
    }

    template<typename... Arg>
    inline void trace(fmt::format_string<Arg...> fmt, Arg &&...args) {
      if (logger->do_logging) {
        logContent["_msg"] = fmt::format(fmt, std::forward<decltype(args)>(args)...);
        do_log(spdlog::level::trace);
      }
    };

    inline void trace() {
      if (logger->do_logging) do_log(spdlog::level::trace);
    }

    template<typename... Arg>
    inline void info(fmt::format_string<Arg...> fmt, Arg &&...args) {
      if (logger->do_logging) {
        logContent["_msg"] = fmt::format(fmt, std::forward<decltype(args)>(args)...);
        do_log(spdlog::level::info);
      }
    };

    inline void info() {
      if (logger->do_logging) do_log(spdlog::level::info);
    }

    template<typename... Arg>
    inline void warn(fmt::format_string<Arg...> fmt, Arg &&...args) {
      if (logger->do_logging) {
        logContent["_msg"] = fmt::format(fmt, std::forward<decltype(args)>(args)...);
        do_log(spdlog::level::warn);
      }
    };

    inline void warn() {
      if (logger->do_logging) do_log(spdlog::level::warn);
    }

    template<typename... Arg>
    [[noreturn]] inline void fatal(fmt::format_string<Arg...> fmt, Arg &&...args) {
      logContent["_msg"] = fmt::format(fmt, std::forward<decltype(args)>(args)...);

      if (logger->do_logging) {
        do_log(spdlog::level::critical);
      }

      spdlog::shutdown();
      throw std::runtime_error(fmt::format("fatal error: {}", logContent.dump(-1)));
    };

    [[noreturn]] inline void fatal() {
      if (logger->do_logging) {
        do_log(spdlog::level::critical);
      }

      spdlog::shutdown();
      throw std::runtime_error(fmt::format("fatal error: {}", logContent.dump(-1)));
    };

  private:
    JsonLogger *logger;
    const char *module_name;
    json logContent;

    void do_log(spdlog::level::level_enum log_type);
  };

  JsonLogger(bool no_logging, bool no_file_logging, bool no_console_logging,
             const std::string &log_path);

  JsonLogger();  // a fake constructor that do nothing, to allow it to exist as a global variable

  ~JsonLogger() = default;

  inline LogBuilder operator()(const char *name) {
    return LogBuilder(this, name);
  }

private:
  std::shared_ptr<spdlog::async_logger> file;
  std::shared_ptr<spdlog::async_logger> console;
  bool do_logging;
};

#define FATAL(context) do {                                                    \
  Log("Fatal").fatal("{}", context);                                                                             \
} while (0)

#define CHECK(cond, context) do {                                              \
  if (!(cond)) \
  Log("Check").fatal("check failed: {} : Assertion ({}) failed at {}:{}", context, #cond, __FILE__, __LINE__); \
} while (0) \

#define CHECK_EQ(val1, val2, context) CHECK(val1 == val2, context)
#define CHECK_NE(val1, val2, context) CHECK(val1 != val2, context)
#define CHECK_LE(val1, val2, context) CHECK(val1 <= val2, context)
#define CHECK_LT(val1, val2, context) CHECK(val1 < val2, context)
#define CHECK_GE(val1, val2, context) CHECK(val1 >= val2, context)
#define CHECK_GT(val1, val2, context) CHECK(val1 > val2, context)

extern JsonLogger Log;
