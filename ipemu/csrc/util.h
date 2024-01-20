#pragma once

#include <cstdint>
#include <fmt/core.h>
#include <fmt/format.h>
#include <optional>

#include "exceptions.h"
#include "spdlog-ext.h"

/// @return: binary[a, b]
inline uint32_t clip(uint32_t binary, int a, int b) {
  CHECK_LE(a, b, "");
  int nbits = b - a + 1;
  uint32_t mask = nbits >= 32 ? (uint32_t)-1 : (1 << nbits) - 1;
  return (binary >> a) & mask;
}

inline bool operator==(const freg_t &a, const freg_t &b) {
  return a.v[0] == b.v[0] && a.v[1] == b.v[1];
}

template <> struct fmt::formatter<freg_t> {
  constexpr auto parse(format_parse_context &ctx) -> decltype(ctx.begin()) {
    return ctx.end();
  }

  template <typename FormatContext>
  auto format(const freg_t &f, FormatContext &ctx) const
      -> decltype(ctx.out()) {
    return fmt::format_to(ctx.out(), "({:016X}, {:016X})", f.v[0], f.v[1]);
  }
};

inline uint8_t n_th_byte(const uint32_t *data, size_t n) {
  return (data[n / 4] >> (8 * (n % 4))) & 0xff;
}

inline bool n_th_bit(const uint32_t *data, size_t n) {
  return (data[n / 32] >> (n % 32)) & 1;
}
