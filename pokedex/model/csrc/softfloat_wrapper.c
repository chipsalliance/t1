#include <assert.h>
#include <softfloat.h>

#include <pokedex-sim_types.h>

// ASL interpreter will suffix all the function with "_N" suffix. For
// non-polymorphic function, it is always "_0". Even for external function, ASLi
// will generate function signature with "_0" suffix. This macro help adjust all
// the function name in one place.
#define ASL_FN(fn) fn##_0

// NOTE: we may replace it with a rm_to_softfloat function once it breaks.
//       It lokks like quite hacky.
static_assert((int)softfloat_round_near_even == (int)RM_RNE);
static_assert((int)softfloat_round_minMag == (int)RM_RTZ);
static_assert((int)softfloat_round_min == (int)RM_RDN);
static_assert((int)softfloat_round_max == (int)RM_RUP);
static_assert((int)softfloat_round_near_maxMag == (int)RM_RMM);

// Ensure softfloat's representation of exception flags
// is the same with RISCV's
static_assert((int)softfloat_flag_inexact == 1);
static_assert((int)softfloat_flag_underflow == 2);
static_assert((int)softfloat_flag_overflow == 4);
static_assert((int)softfloat_flag_infinite == 8);
static_assert((int)softfloat_flag_invalid == 16);

static void set_rounding_mode_clear_fflags(RM rm) {
  softfloat_roundingMode = rm;
  softfloat_exceptionFlags = 0;
}

static void clear_fflags() {
  // Defensive programming,
  // we clear roundingMode even if the operation does not depend on it.
  softfloat_roundingMode = softfloat_round_near_even;

  softfloat_exceptionFlags = 0;
}

F32_Flags ASL_FN(riscv_f32_add)(RM rm, uint32_t x, uint32_t y) {
  set_rounding_mode_clear_fflags(rm);

  float32_t xx = { .v = x };
  float32_t yy = { .v = y };

  F32_Flags res;
  res.value = f32_add(xx, yy).v;
  res.fflags = softfloat_exceptionFlags;
  return res;
}

F32_Flags ASL_FN(riscv_f32_sub)(RM rm, uint32_t x, uint32_t y) {
  set_rounding_mode_clear_fflags(rm);

  float32_t xx = { .v = x };
  float32_t yy = { .v = y };

  F32_Flags res;
  res.value = f32_sub(xx, yy).v;
  res.fflags = softfloat_exceptionFlags;
  return res;
}

F32_Flags ASL_FN(riscv_f32_mul)(RM rm, uint32_t x, uint32_t y) {
  set_rounding_mode_clear_fflags(rm);

  float32_t xx = { .v = x };
  float32_t yy = { .v = y };

  F32_Flags res;
  res.value = f32_mul(xx, yy).v;
  res.fflags = softfloat_exceptionFlags;
  return res;
}

F32_Flags ASL_FN(riscv_f32_div)(RM rm, uint32_t x, uint32_t y) {
  set_rounding_mode_clear_fflags(rm);

  float32_t xx = { .v = x };
  float32_t yy = { .v = y };

  F32_Flags res;
  res.value = f32_div(xx, yy).v;
  res.fflags = softfloat_exceptionFlags;
  return res;
}

F32_Flags ASL_FN(riscv_f32_sqrt)(RM rm, uint32_t x) {
  set_rounding_mode_clear_fflags(rm);

  float32_t xx = { .v = x };

  F32_Flags res;
  res.value = f32_sqrt(xx).v;
  res.fflags = softfloat_exceptionFlags;
  return res;
}

F32_Flags ASL_FN(riscv_f32_mulAdd)(RM rm, uint32_t x, uint32_t y, uint32_t z) {
  set_rounding_mode_clear_fflags(rm);

  float32_t xx = { .v = x };
  float32_t yy = { .v = y };
  float32_t zz = { .v = z };

  F32_Flags res;
  res.value = f32_mulAdd(xx, yy, zz).v;
  res.fflags = softfloat_exceptionFlags;
  return res;
}

Bool_Flags ASL_FN(riscv_f32_eqQuiet)(uint32_t x, uint32_t y) {
  clear_fflags();

  float32_t xx = { .v = x };
  float32_t yy = { .v = y };

  Bool_Flags res;
  res.value = f32_eq(xx, yy);
  res.fflags = softfloat_exceptionFlags;
  return res;
}

Bool_Flags ASL_FN(riscv_f32_ltSignaling)(uint32_t x, uint32_t y) {
  clear_fflags();

  float32_t xx = { .v = x };
  float32_t yy = { .v = y };

  Bool_Flags res;
  res.value = f32_lt(xx, yy);
  res.fflags = softfloat_exceptionFlags;
  return res;
}

Bool_Flags ASL_FN(riscv_f32_leSignaling)(uint32_t x, uint32_t y) {
  clear_fflags();

  float32_t xx = { .v = x };
  float32_t yy = { .v = y };

  Bool_Flags res;
  res.value = f32_le(xx, yy);
  res.fflags = softfloat_exceptionFlags;
  return res;
}

F32_Flags ASL_FN(riscv_f32_fromSInt32)(RM rm, uint32_t x) {
  set_rounding_mode_clear_fflags(rm);

  F32_Flags res;
  res.value = i32_to_f32((int32_t)x).v;
  res.fflags = softfloat_exceptionFlags;
  return res;
}

F32_Flags ASL_FN(riscv_f32_fromUInt32)(RM rm, uint32_t x) {
  set_rounding_mode_clear_fflags(rm);

  F32_Flags res;
  res.value = ui32_to_f32(x).v;
  res.fflags = softfloat_exceptionFlags;
  return res;
}

Bits32_Flags ASL_FN(riscv_f32_toSInt32)(RM rm, uint32_t x) {
  clear_fflags();

  float32_t xx = { .v = x };

  Bits32_Flags res;
  res.value = (uint32_t)f32_to_i32(xx, rm, true);
  res.fflags = softfloat_exceptionFlags;
  return res;
}

Bits32_Flags ASL_FN(riscv_f32_toUInt32)(RM rm, uint32_t x) {
  clear_fflags();

  float32_t xx = { .v = x };

  Bits32_Flags res;
  res.value = f32_to_ui32(xx, rm, true);
  res.fflags = softfloat_exceptionFlags;
  return res;
}
