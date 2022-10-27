#pragma once

#include <cstdint>

/// @return: binary[a, b]
inline uint32_t clip(uint32_t binary, int a, int b) { return (binary >> a) & ((1 << (b - a + 1)) - 1); }

inline bool is_vector_instr(uint64_t f) {
  uint32_t opcode = clip(f, 0, 6);
  uint32_t width = clip(f, 12, 14);

  bool is_load_type = opcode == 0b111;
  bool is_store_type = opcode == 0b100111;
  bool is_v_type = opcode == 0b1010111 && width != 0b111;

  if (is_load_type || is_store_type || is_v_type) {
    return true;
  } else {
    return false;
  }
}
