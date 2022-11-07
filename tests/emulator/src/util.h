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

/// back-port of `std::erase_if` in C++ 20.
/// refer to https://en.cppreference.com/w/cpp/container/map/erase_if
template< class Key, class T, class Compare, class Alloc, class Pred >
typename std::map<Key,T,Compare,Alloc>::size_type
erase_if( std::map<Key,T,Compare,Alloc>& c, Pred pred ) {
  auto old_size = c.size();
  for (auto i = c.begin(), last = c.end(); i != last; ) {
    if (pred(*i)) {
      i = c.erase(i);
    } else {
      ++i;
    }
  }
  return old_size - c.size();
};