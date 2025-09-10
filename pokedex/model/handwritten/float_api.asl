// float_api.asl act as glue file to perform calculation and data type
// conversion between ASL model and softfloat model.
//
// This file contains only *pure* softfloat operation: functions are operate
// with only pure value in and pure value out, no ASL internal architecture
// states will be modified during function execution (softfloat states might be
// changed though).

let CANONICAL_NAN : bits(32) = (0x7fc0_0000)[31:0];

func f32_is_not_a_number(v : bits(32)) => boolean
begin
  return IsOnes(v[30:23]) && !(IsZero(v[22:0]));
end

func f32_is_negative_zero(v : bits(32)) => boolean
begin
  return v[31] == '1' && IsZero(v[30:0]);
end

func f32_is_signal_nan(v : bits(32)) => boolean
begin
  return ffi_f32_isSignalingNaN(v);
end

func RM_to_softfloat(rm : RM) => bits(3)
begin
  case rm of
    when RM_RNE => return '000';
    when RM_RTZ => return '001';
    when RM_RDN => return '010';
    when RM_RUP => return '011';
    when RM_RMM => return '100';
  end
end

// Pack up softfloat calculation result and hand over to instruction handler
record FloatOpResult {
  data : bits(32);
  xcpt : integer;
};

// Add two bit-vector base on given rounding mode, returning calculation result and softfloat exception flag.
func f32_add(s1 : bits(32), s2 : bits(32), rm : RM) => FloatOpResult
begin
  let data : bits(32) = ffi_f32_add(s1, s2, RM_to_softfloat(rm));
  let xcpt : integer = ffi_yield_softfloat_exception_flags();

  return FloatOpResult {
    data = data,
    xcpt = xcpt
  };
end

// Substract two bit-vector base on given rounding mode, returning calculation result and softfloat exception flag.
func f32_sub(s1 : bits(32), s2 : bits(32), rm : RM) => FloatOpResult
begin
  let data : bits(32) = ffi_f32_sub(s1, s2, RM_to_softfloat(rm));
  let xcpt : integer = ffi_yield_softfloat_exception_flags();

  return FloatOpResult {
    data = data,
    xcpt = xcpt
  };
end

// Multiply two bit-vector base on given rounding mode, returning calculation result and softfloat exception flag.
func f32_mul(s1 : bits(32), s2 : bits(32), rm : RM) => FloatOpResult
begin
  let data : bits(32) = ffi_f32_mul(s1, s2, RM_to_softfloat(rm));
  let xcpt : integer = ffi_yield_softfloat_exception_flags();

  return FloatOpResult {
    data = data,
    xcpt = xcpt
  };
end

// Multiply two bit-vector base on given rounding mode, returning calculation result and softfloat exception flag.
func f32_div(s1 : bits(32), s2 : bits(32), rm : RM) => FloatOpResult
begin
  let data : bits(32) = ffi_f32_div(s1, s2, RM_to_softfloat(rm));
  let xcpt : integer = ffi_yield_softfloat_exception_flags();

  return FloatOpResult {
    data = data,
    xcpt = xcpt
  };
end

func f32_sqrt(src : bits(32), rm : RM) => FloatOpResult
begin
  let data : bits(32) = ffi_f32_sqrt(src, RM_to_softfloat(rm));
  let xcpt : integer = ffi_yield_softfloat_exception_flags();

  return FloatOpResult {
    data = data,
    xcpt = xcpt
  };
end

func f32_min(s1 : bits(32), s2 : bits(32)) => FloatOpResult
begin
  // Force softfloat to run once to set invalid flag when "Signal NaN" met on any of the
  // operand, so that we don't need to return invalid ourselves, keeping
  // consistency with other softfloat operation.
  let is_lt = ffi_f32_lt_quiet(s1, s2) || (ffi_f32_eq(s1, s2) && f32_is_negative_zero(s1));
  let xcpt = ffi_yield_softfloat_exception_flags();

  // if both are NaN
  if f32_is_not_a_number(s1) && f32_is_not_a_number(s2) then
    return FloatOpResult {
      data = CANONICAL_NAN,
      xcpt = xcpt
    };
  end

  // if s1 is less than s2, or s2 is a NaN
  if is_lt || f32_is_not_a_number(s2) then
    return FloatOpResult {
      data = s1,
      xcpt = xcpt
    };
  end

  return FloatOpResult {
    data = s2,
    xcpt = xcpt
  };
end

func f32_max(s1 : bits(32), s2 : bits(32)) => FloatOpResult
begin
  // Force softfloat to run once to set invalid flag when "Signal NaN" met on any of the
  // operand, so that we don't need to return invalid ourselves, keeping
  // consistency with other softfloat operation.
  let is_gt = ffi_f32_lt_quiet(s2, s1) || (ffi_f32_eq(s1, s2) && f32_is_negative_zero(s2));
  let xcpt = ffi_yield_softfloat_exception_flags();

  // if both are NaN
  if f32_is_not_a_number(s1) && f32_is_not_a_number(s2) then
    return FloatOpResult {
      data = CANONICAL_NAN,
      xcpt = xcpt
    };
  end

  // if s1 is greater than s2, or s2 is a NaN
  if is_gt || f32_is_not_a_number(s2) then
    return FloatOpResult {
      data = s1,
      xcpt = xcpt
    };
  end

  return FloatOpResult {
    data = s2,
    xcpt = xcpt
  };
end

func f32_multiply_add(s1 : bits(32), s2 : bits(32), s3 : bits(32), rm : RM) => FloatOpResult
begin
  let data : bits(32) = ffi_f32_mulAdd(s1, s2, s3, RM_to_softfloat(rm));
  let xcpt : integer = ffi_yield_softfloat_exception_flags();

  return FloatOpResult {
    data = data,
    xcpt = xcpt
  };
end

record FloatCvtResult {
  data : integer;
  xcpt : integer;
};

func f32_to_i32(src : bits(32), rm : RM) => FloatCvtResult
begin
  return FloatCvtResult {
    data = ffi_f32_to_i32(src, RM_to_softfloat(rm)),
    xcpt = ffi_yield_softfloat_exception_flags()
  };
end

func f32_to_ui32(src : bits(32), rm : RM) => FloatCvtResult
begin
  return FloatCvtResult {
    data = ffi_f32_to_ui32(src, RM_to_softfloat(rm)),
    xcpt = ffi_yield_softfloat_exception_flags()
  };
end

func i32_to_f32(src : bits(32), rm : RM) => FloatOpResult
begin
  return FloatOpResult {
    data = ffi_i32_to_f32(src, RM_to_softfloat(rm)),
    xcpt = ffi_yield_softfloat_exception_flags()
  };
end

func ui32_to_f32(src : bits(32), rm : RM) => FloatOpResult
begin
  return FloatOpResult {
    data = ffi_ui32_to_f32(src, RM_to_softfloat(rm)),
    xcpt = ffi_yield_softfloat_exception_flags()
  };
end

record FloatCmpResult {
  hold : boolean;
  xcpt : integer;
};

func f32_lt(rs1 : bits(32), rs2 : bits(32)) => FloatCmpResult
begin
  return FloatCmpResult {
    hold = ffi_f32_lt(rs1, rs2),
    xcpt = ffi_yield_softfloat_exception_flags()
  };
end

func f32_le(rs1 : bits(32), rs2 : bits(32)) => FloatCmpResult
begin
  return FloatCmpResult {
    hold = ffi_f32_le(rs1, rs2),
    xcpt = ffi_yield_softfloat_exception_flags()
  };
end

func f32_eq(rs1 : bits(32), rs2 : bits(32)) => FloatCmpResult
begin
  return FloatCmpResult {
    hold = ffi_f32_eq(rs1, rs2),
    xcpt = ffi_yield_softfloat_exception_flags()
  };
end
