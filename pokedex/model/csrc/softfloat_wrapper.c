#include <softfloat.h>

// ASL interpreter will suffix all the function with "_N" suffix. For
// non-polymorphic function, it is always "_0". Even for external function, ASLi
// will generate function signature with "_0" suffix. This macro help adjust all
// the function name in one place.
#define ASL_FN(fn) fn##_0

// `set_rounding_mode` set the current softfloat global rounding mode to
// `rm`.
void set_rounding_mode(uint_fast8_t rm) { softfloat_roundingMode = rm; }

// `asl_bits32_to_f32` pack the 32-bit ASL bit vector to softfloat `float32_t` type
inline float32_t asl_bits32_to_f32(uint_fast32_t bits) { return (float32_t){bits}; }

// `ffi_get_softfloat_exception_flags` returns the current exception flags in softfloat global namespace
uint_fast8_t ASL_FN(ffi_yield_softfloat_exception_flags)() {
  uint_fast8_t xcpt = softfloat_exceptionFlags;
  softfloat_exceptionFlags = 0;
  return xcpt;
}

// `ffi_f32_add` convert two ASL `bits(32)` value to softfloat `float32_t`
// type and do "add" calculation. Returning bit representation of the "add"
// result.
uint_fast32_t ASL_FN(ffi_f32_add)(uint_fast32_t rs1, uint_fast32_t rs2, uint_fast8_t rm) {
  set_rounding_mode(rm);

  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  float32_t result = f32_add(s1, s2);

  return result.v;
}

// `ffi_f32_sub` convert two ASL `bits(32)` value to softfloat `float32_t`
// type and do "sub" calculation. Returning bit representation of the "sub"
// result.
uint_fast32_t ASL_FN(ffi_f32_sub)(uint_fast32_t rs1, uint_fast32_t rs2, uint_fast8_t rm) {
  set_rounding_mode(rm);

  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  float32_t result = f32_sub(s1, s2);

  return result.v;
}

// `ffi_f32_mul` convert two ASL `bits(32)` value to softfloat `float32_t`
// type and do "sub" calculation. Returning bit representation of the "sub"
// result.
uint_fast32_t ASL_FN(ffi_f32_mul)(uint_fast32_t rs1, uint_fast32_t rs2, uint_fast8_t rm) {
  set_rounding_mode(rm);

  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  float32_t result = f32_mul(s1, s2);

  return result.v;
}

// `ffi_f32_div` convert two ASL `bits(32)` value to softfloat `float32_t`
// type and do "sub" calculation. Returning bit representation of the "sub"
// result.
uint_fast32_t ASL_FN(ffi_f32_div)(uint_fast32_t rs1, uint_fast32_t rs2, uint_fast8_t rm) {
  set_rounding_mode(rm);

  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  float32_t result = f32_div(s1, s2);

  return result.v;
}

// `ffi_f32_sqrt` convert two ASL `bits(32)` value to softfloat `float32_t`
// type and do "sub" calculation. Returning bit representation of the "sub"
// result.
uint_fast32_t ASL_FN(ffi_f32_sqrt)(uint_fast32_t src, uint_fast8_t rm) {
  set_rounding_mode(rm);

  float32_t operand = asl_bits32_to_f32(src);

  float32_t result = f32_sqrt(operand);

  return result.v;
}

// `ffi_f32_lt` compare the two operand and return boolean value of first one is less than second one.
// When either of the input is NaN, invalid flag will be set, and less than operation is consider fail and false is return.
bool ASL_FN(ffi_f32_lt)(uint_fast32_t rs1, uint_fast32_t rs2) {
  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  return f32_lt(s1, s2);
}

// `ffi_f32_le` compare the two operand and return boolean value of first one is less or equal with second one.
// When either of the input is NaN, invalid flag will be set, and less than operation is consider fail and false is return.
bool ASL_FN(ffi_f32_le)(uint_fast32_t rs1, uint_fast32_t rs2) {
  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  return f32_le(s1, s2);
}

// `ffi_f32_lt_quiet` compare the two operand and return boolean value of first one is less than second one
bool ASL_FN(ffi_f32_lt_quiet)(uint_fast32_t rs1, uint_fast32_t rs2) {
  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  return f32_lt_quiet(s1, s2);
}

// `ffi_f32_eq` compare the two operand and return boolean value of their are equal
bool ASL_FN(ffi_f32_eq)(uint_fast32_t rs1, uint_fast32_t rs2) {
  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);

  return f32_eq(s1, s2);
}

// `ffi_f32_mulAdd` calculate (f32(rs1) * f32(rs2)) + f32(rs3) and return bit representation of the result
uint_fast32_t ASL_FN(ffi_f32_mulAdd)(uint_fast32_t rs1, uint_fast32_t rs2, uint_fast32_t rs3, uint_fast8_t rm) {
  set_rounding_mode(rm);

  float32_t s1 = asl_bits32_to_f32(rs1);
  float32_t s2 = asl_bits32_to_f32(rs2);
  float32_t s3 = asl_bits32_to_f32(rs3);

  float32_t rd = f32_mulAdd(s1, s2, s3);

  return rd.v;
}

// `ffi_f32_to_i32` convert inputs from floating point number to signed integer
int_fast32_t ASL_FN(ffi_f32_to_i32)(uint_fast32_t src, uint_fast8_t rm) {
  set_rounding_mode(rm);

  float32_t s1 = asl_bits32_to_f32(src);

  // all fp conversion set inexact flag
  return f32_to_i32(s1, /*rounding mode*/ rm, /*exact*/ true);
}

// `ffi_f32_to_u32` convert inputs from floating point number to unsigned integer
uint_fast32_t ASL_FN(ffi_f32_to_ui32)(uint_fast32_t src, uint_fast8_t rm) {
  set_rounding_mode(rm);

  float32_t s1 = asl_bits32_to_f32(src);

  // all fp conversion set inexact flag
  return f32_to_ui32(s1, /*rounding mode*/ rm, /*exact*/ true);
}

// `ffi_i32_to_f32` convert signed int 32 bit number to floating point number
uint_fast32_t ASL_FN(ffi_i32_to_f32)(uint_fast32_t src, uint_fast8_t rm) {
  set_rounding_mode(rm);

  // convert bit representation to sign number
  int_fast32_t s1 = (int_fast32_t)src;

  float32_t rd = i32_to_f32(s1);

  return rd.v;
}

// `ffi_u32_to_f32` convert unsigned int 32 bit number to floating point number
uint_fast32_t ASL_FN(ffi_ui32_to_f32)(uint_fast32_t src, uint_fast8_t rm) {
  set_rounding_mode(rm);

  float32_t rd = ui32_to_f32(src);

  return rd.v;
}

// `ffi_f32_isSignalingNaN` return true if given float number is signaling NaN
uint_fast32_t ASL_FN(ffi_f32_isSignalingNaN)(uint_fast32_t src) {
  float32_t s1 = asl_bits32_to_f32(src);
  return f32_isSignalingNaN(s1);
}
