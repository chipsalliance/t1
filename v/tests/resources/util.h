#pragma once

#include <cstdint>

inline uint32_t clip(uint32_t binary, int a, int b) { return (binary >> a) & ((1 << (b - a + 1)) - 1); }

inline bool is_vector_instr(uint64_t f) {
  uint32_t opcode = clip(f, 0, 6);

  if ((opcode & 0b1011111) == 0b0000111 || opcode == 0b1010111) {
    return true;
  } else {
    return false;
  }
}
