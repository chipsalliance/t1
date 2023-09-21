#pragma once

#include <iostream>
#include <nlohmann/json.hpp>
#include <optional>
#include <spdlog/spdlog.h>
using json = nlohmann::json;

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

void setup_logger();

class Log {
private:
  json internal;

  inline std::string dump() {
    std::string ret;
    try {
      ret = internal.dump(/*indent=*/2);
    } catch (json::type_error &ex) {
      throw std::runtime_error(fmt::format("JSON error for log '{}': {}",
                                           internal["name"], ex.what()));
    }
    return ret;
  }

public:
  Log(const char *n) { internal["name"] = n; }
  ~Log() = default;

  template <typename T> Log &with(const char *key, T value) {
    internal["info"][key] = value;
    return *this;
  }

  // Overload the index operator
  json &operator[](const char *key) { return internal["info"][key]; };

  inline void info() { spdlog::info("{}", this->dump()); }
  inline void trace() { spdlog::trace("{}", this->dump()); }

  template <typename... Arg>
  inline void info(fmt::format_string<Arg...> fmt, Arg &&...args) {
    auto msg = fmt::format(fmt, args...);
    internal["message"] = msg;
    spdlog::info("{}", this->dump());
  }

  template <typename... Arg>
  inline void warn(fmt::format_string<Arg...> fmt, Arg &&...args) {
    auto msg = fmt::format(fmt, args...);
    internal["message"] = msg;
    spdlog::warn("{}", this->dump());
  }

  template <typename... Arg>
  inline void critical(fmt::format_string<Arg...> fmt, Arg &&...args) {
    auto msg = fmt::format(fmt, args...);
    internal["message"] = msg;
    spdlog::critical("{}", this->dump());
  }

  template <typename... Arg>
  inline void trace(fmt::format_string<Arg...> fmt, Arg &&...args) {
    auto msg = fmt::format(fmt, args...);
    internal["message"] = msg;
    spdlog::trace("{}", this->dump());
  };

  template <typename... Arg>
  inline void fatal(fmt::format_string<Arg...> fmt, Arg &&...args) {
    auto msg = fmt::format(fmt, args...);
    internal["message"] = msg;

    spdlog::critical("{}", this->dump());
    spdlog::shutdown();

    throw std::runtime_error(msg);
  };
};
