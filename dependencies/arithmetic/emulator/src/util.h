#pragma once

#include <cstdint>

inline char *get_env_arg(const char *name) {
  char *val = std::getenv(name);
  CHECK(val != nullptr) << fmt::format("cannot find environment of name '{}'", name);
  return val;
}