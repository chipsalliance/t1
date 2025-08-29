#include <softfloat.h>

// ASL interpreter will suffix all the function with "_N" suffix. For
// non-polymorphic function, it is always "_0". Even for external function, ASLi
// will generate function signature with "_0" suffix. This macro help adjust all
// the function name in one place.
#define ASL_FN(fn) fn##_0

// `set_rounding_mode` set the current softfloat global rounding mode to
// `rm`.
void set_rounding_mode(uint8_t rm) { softfloat_roundingMode = rm; }

// `asl_bits32_to_f32` pack the 32-bit ASL bit vector to softfloat `float32_t` type
inline float32_t asl_bits32_to_f32(uint32_t bits) { return (float32_t){bits}; }

// `ffi_get_softfloat_exception_flags` returns the current exception flags in softfloat global namespace
uint8_t ASL_FN(ffi_yield_softfloat_exception_flags)() {
  uint_fast8_t xcpt = softfloat_exceptionFlags;
  softfloat_exceptionFlags = 0;
  return xcpt;
}

// `ffi_f32_add` convert two ASL `bits(32)` value to softfloat `float32_t`
// type and do "add" calculation. Returning bit representation of the "add"
// result.
uint32_t ASL_FN(ffi_f32_add)(uint32_t rs1, uint32_t rs2, uint8_t rm) {
  set_rounding_mode(rm);

  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  float32_t result = f32_add(s1, s2);

  return result.v;
}

// `ffi_f32_sub` convert two ASL `bits(32)` value to softfloat `float32_t`
// type and do "sub" calculation. Returning bit representation of the "sub"
// result.
uint32_t ASL_FN(ffi_f32_sub)(uint32_t rs1, uint32_t rs2, uint8_t rm) {
  set_rounding_mode(rm);

  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  float32_t result = f32_sub(s1, s2);

  return result.v;
}

// `ffi_f32_mul` convert two ASL `bits(32)` value to softfloat `float32_t`
// type and do "sub" calculation. Returning bit representation of the "sub"
// result.
uint32_t ASL_FN(ffi_f32_mul)(uint32_t rs1, uint32_t rs2, uint8_t rm) {
  set_rounding_mode(rm);

  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  float32_t result = f32_mul(s1, s2);

  return result.v;
}

// `ffi_f32_div` convert two ASL `bits(32)` value to softfloat `float32_t`
// type and do "sub" calculation. Returning bit representation of the "sub"
// result.
uint32_t ASL_FN(ffi_f32_div)(uint32_t rs1, uint32_t rs2, uint8_t rm) {
  set_rounding_mode(rm);

  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  float32_t result = f32_div(s1, s2);

  return result.v;
}

// `ffi_f32_sqrt` convert two ASL `bits(32)` value to softfloat `float32_t`
// type and do "sub" calculation. Returning bit representation of the "sub"
// result.
uint32_t ASL_FN(ffi_f32_sqrt)(uint32_t src, uint8_t rm) {
  set_rounding_mode(rm);

  float32_t operand = asl_bits32_to_f32(src);

  float32_t result = f32_sqrt(operand);

  return result.v;
}

// `ffi_f32_lt_quiet` compare the two operand and return boolean value of first one is less than second one
bool ASL_FN(ffi_f32_lt_quiet)(uint32_t rs1, uint32_t rs2) {
  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  return f32_lt_quiet(s1, s2);
}

// `ffi_f32_eq` compare the two operand and return boolean value of their are equal
bool ASL_FN(ffi_f32_eq)(uint32_t rs1, uint32_t rs2) {
  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  return f32_eq(s1, s2);
}

// `ffi_f32_gt_quiet` compare the two operand and return boolean value of first one is greater than second one
uint32_t ASL_FN(ffi_f32_gt_quiet)(uint32_t rs1, uint32_t rs2) {
  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  return f32_gt_quiet(s1, s2);
}
