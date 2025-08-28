#include <softfloat.h>

// `ffi_set_rounding_mode` set the current softfloat global rounding mode to
// `rm`.
void ffi_set_rounding_mode_0(uint8_t rm) { softfloat_roundingMode = rm; }

inline float32_t asl_bits32_to_f32(uint32_t bits) { return (float32_t){bits}; }

// `ffi_f32_add_0` convert two ASL `bits(32)` value to softfloat `float32_t`
// type and do "add" calculation. Returning bit representation of the "add"
// result.
uint32_t ffi_f32_add_0(uint32_t rs1, uint32_t rs2) {
  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  float32_t rd = f32_add(s1, s2);
  return rd.v;
}
