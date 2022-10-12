#pragma once

#include <cstdint>

inline uint32_t clip(uint32_t binary, int a, int b) { return (binary >> a) & ((1 << (b - a + 1)) - 1); }

inline bool is_vector_instr(uint64_t f) {
  uint32_t opcode = clip(f, 0, 6);
  uint32_t width = clip(f, 12, 14);

  auto load_type = opcode == 0b111;
  auto store_type = opcode == 0b100111;
  auto v_type = opcode == 0b1010111 && width != 0b111;

  if (load_type || store_type || v_type) {
    return true;
  } else {
    return false;
  }
}
