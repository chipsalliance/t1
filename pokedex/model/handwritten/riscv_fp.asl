type F32_Flags of record {
  value: bits(32),
  fflags: bits(5)
};

type Bool_Flags of record {
  value: boolean,
  fflags: bits(5)
};

type Bits32_Flags of record {
  value: bits(32),
  fflags: bits(5)
};

// RISC-V floating-point operations does not perform NaN propagation.
// When an arithmetic operation generates a NaN,
// it always generates the (quiet) RSIC-V canonical NaN.

constant F32_CANONICAL_NAN : bits(32) = (0x7fc0_0000)[31:0];

// IEEE 754-2019: "addition" operation
// NOTE: this function is implemented in softfloat_wrapper.c
func riscv_f32_add(frm: RM, x: bits(32), y: bits(32)) => F32_Flags;

// IEEE 754-2019: "substraction" operation
// NOTE: this function is implemented in softfloat_wrapper.c
func riscv_f32_sub(frm: RM, x: bits(32), y: bits(32)) => F32_Flags;

// IEEE 754-2019: "multiplication" operation
// NOTE: this function is implemented in softfloat_wrapper.c
func riscv_f32_mul(frm: RM, x: bits(32), y: bits(32)) => F32_Flags;

// IEEE 754-2019: "division" operation
// NOTE: this function is implemented in softfloat_wrapper.c
func riscv_f32_div(frm: RM, x: bits(32), y: bits(32)) => F32_Flags;

// IEEE 754-2019: "squareRoot" operation
// NOTE: this function is implemented in softfloat_wrapper.c
func riscv_f32_sqrt(frm: RM, x: bits(32)) => F32_Flags;

// IEEE 754-2019: "fusedMultiplyAdd" operation, computes x * y + z
// - RISC-V requires "0 * inf + qNaN" to raise invalid fp exception
// NOTE: this function is implemented in softfloat_wrapper.c
func riscv_f32_mulAdd(frm: RM, x: bits(32), y: bits(32), z: bits(32)) => F32_Flags;

// IEEE 754-2019: "compareSignalingLess" operation
// NOTE: this function is implemented in softfloat_wrapper.c
func riscv_f32_ltSignaling(x: bits(32), y: bits(32)) => Bool_Flags;

// IEEE 754-2019: "compareSignalingLessThan" operation
// NOTE: this function is implemented in softfloat_wrapper.c
func riscv_f32_leSignaling(x: bits(32), y: bits(32)) => Bool_Flags;

// IEEE 754-2019: "compareQuietEqual" operation
// NOTE: this function is implemented in softfloat_wrapper.c
func riscv_f32_eqQuiet(x: bits(32), y: bits(32)) => Bool_Flags;

func riscv_f32_fromSInt32(frm: RM, x: bits(32)) => F32_Flags;
func riscv_f32_fromUInt32(frm: RM, x: bits(32)) => F32_Flags;

// IEEE 754-2019: "convertToIntegerExact" operation family
// - RISC-V requires the result saturated when the result is out of bound,
//   including the case that the input is +- inf.
// - RISC-V requires the result is (2^31-1) if the input is NaN
// NOTE: this function is implemented in softfloat_wrapper.c
func riscv_f32_toSInt32(frm: RM, x: bits(32)) => Bits32_Flags;

// IEEE 754-2019: "convertToIntegerExact" operation family
// - RISC-V requires the result saturated when the result is out of bound,
//   including the case that the input is +- inf.
// - RISC-V requires the result is (2^32-1) if the input is NaN
// NOTE: this function is implemented in softfloat_wrapper.c
func riscv_f32_toUInt32(frm: RM, x: bits(32)) => Bits32_Flags;

func riscv_f32_mulAddGeneric(frm: RM, x: bits(32), y: bits(32), z: bits(32), inv_prod: boolean, inv_rs3: boolean) => F32_Flags
begin
  // Since riscv always return canonical NaNs (not doing NaN propagation),
  // we could preprocess operands to emulate generic fma

  var xx = x;
  var zz = z;
  if inv_prod then
    xx[31] = NOT x[31];
  end
  if inv_rs3 then
    zz[31] = NOT z[31];
  end

  return riscv_f32_mulAdd(frm, xx, y, zz);
end

// IEEE 754-2019: "minimumNumber" operation
func riscv_f32_minNum(x: bits(32), y: bits(32)) => F32_Flags
begin
  var invalid = '0';
  if f32_isSignalingNan(x) || f32_isSignalingNan(y) then
    invalid = '1';
  end

  var res: bits(32);
  if f32_isNan(x) then
    if f32_isNan(y) then
      res = F32_CANONICAL_NAN;
    else
      res = y;
    end
  else
    if f32_isNan(y) then
      res = x;
    else
      if f32_ltTotalOrder(x, y) then
        res = x;
      else
        res = y;
      end
    end
  end

  return F32_Flags {
    value = res,
    fflags = [invalid, '0000']
  };
end

// IEEE 754-2019: "maximumNumber" operation
func riscv_f32_maxNum(x: bits(32), y: bits(32)) => F32_Flags
begin
    var invalid = '0';
  if f32_isSignalingNan(x) || f32_isSignalingNan(y) then
    invalid = '1';
  end

  var res: bits(32);
  if f32_isNan(x) then
    if f32_isNan(y) then
      res = F32_CANONICAL_NAN;
    else
      res = y;
    end
  else
    if f32_isNan(y) then
      res = x;
    else
      if f32_ltTotalOrder(x, y) then
        res = y;
      else
        res = x;
      end
    end
  end

  return F32_Flags {
    value = res,
    fflags = [invalid, '0000']
  };
end

constant FCLASS_INFINITE_NEGATIVE = 0;
constant FCLASS_NORMAL_NEGATIVE = 1;
constant FCLASS_SUBNORMAL_NEGATIVE = 2;
constant FCLASS_ZERO_NEGATIVE = 3;
constant FCLASS_ZERO_POSITIVE = 4;
constant FCLASS_SUBNORMAL_POSITIVE = 5;
constant FCLASS_NORMAL_POSITIVE = 6;
constant FCLASS_INFINITE_POSITIVE = 7;
constant FCLASS_SIGNALING_NAN = 8;
constant FCLASS_QUIET_NAN = 9;

func f32_isNan(v: bits(32)) => boolean
begin
  return IsOnes(v[30:23]) && !(IsZero(v[22:0]));
end

func f32_isSignalingNan(v : bits(32)) => boolean
begin
  return IsOnes(v[30:23]) && v[22] == '0' && !(IsZero(v[21:0]));
end

func f32_ltTotalOrder(x: bits(32), y: bits(32)) => boolean
begin
  case [x[31], y[31]] of
    when '00' => return UInt(x) < UInt(y);
    when '01' => return FALSE;
    when '10' => return TRUE;
    when '11' => return UInt(x) > UInt(y);
  end
end

// IEEE 754-2019: "class" operation.
// The result is a one-hot bit vector,
// using the same format as RISC-V fclass.{s,d} instruction.
func riscv_fclass_f32(x: bits(32)) => bits(10)
begin
  let sign : bit = x[31];
  let exp : bits(8) = x[30:23];
  let mantissa : bits(23) = x[22:0];
  let quiet_bit : bit = mantissa[22];

  var mask: bits(10) = Zeros(10);
  if IsOnes(exp) then
    // NaN or infinity

    if IsZero(mantissa) then
      if sign == '1' then
        mask[FCLASS_INFINITE_NEGATIVE] = '1';
      else
        mask[FCLASS_INFINITE_POSITIVE] = '1';
      end
    elsif quiet_bit == '1' then
      mask[FCLASS_QUIET_NAN] = '1';
    else
      mask[FCLASS_SIGNALING_NAN] = '1';
    end
  elsif IsZero(exp) then
    // zero or subnornal

    if IsZero(mantissa) then
      // zero
      if sign == '1' then
        mask[FCLASS_ZERO_NEGATIVE] = '1';
      else
        mask[FCLASS_ZERO_POSITIVE] = '1';
      end
    else
      // subnormal
      if sign == '1' then
        mask[FCLASS_SUBNORMAL_NEGATIVE] = '1';
      else
        mask[FCLASS_SUBNORMAL_POSITIVE] = '1';
      end
    end
  else
    // normal number

    if sign == '1' then
      mask[FCLASS_NORMAL_NEGATIVE] = '1';
    else
      mask[FCLASS_NORMAL_POSITIVE] = '1';
    end
  end

  assert(!IsZero(mask));
  return mask;
end