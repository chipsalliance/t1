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

func riscv_f32_add(frm: RM, x: bits(32), y: bits(32)) => F32_Flags;
func riscv_f32_sub(frm: RM, x: bits(32), y: bits(32)) => F32_Flags;
func riscv_f32_mul(frm: RM, x: bits(32), y: bits(32)) => F32_Flags;
func riscv_f32_div(frm: RM, x: bits(32), y: bits(32)) => F32_Flags;
func riscv_f32_sqrt(frm: RM, x: bits(32)) => F32_Flags;

// x * y + z
func riscv_f32_mulAdd(frm: RM, x: bits(32), y: bits(32), z: bits(32)) => F32_Flags;

func riscv_f32_ltSignaling(x: bits(32), y: bits(32)) => Bool_Flags;
func riscv_f32_leSignaling(x: bits(32), y: bits(32)) => Bool_Flags;
func riscv_f32_eqQuiet(x: bits(32), y: bits(32)) => Bool_Flags;
func riscv_f32_fromSInt32(frm: RM, x: bits(32)) => F32_Flags;
func riscv_f32_fromUInt32(frm: RM, x: bits(32)) => F32_Flags;
func riscv_f32_toSInt32(frm: RM, x: bits(32)) => Bits32_Flags;
func riscv_f32_toUInt32(frm: RM, x: bits(32)) => Bits32_Flags;

func riscv_f32_minNum(x: bits(32), y: bits(32)) => F32_Flags;
func riscv_f32_maxNum(x: bits(32), y: bits(32)) => F32_Flags;

func riscv_f32_mulAddGeneric(frm: RM, x: bits(32), y: bits(32), z: bits(32), inv_prod: boolean, inv_rs3: boolean) => F32_Flags
begin
  var xx = x;
  var zz = z;
  if inv_prod then
    xx[31] = NOT x[31];
  end
  if inv_rs3 then
    zz[31] = NOT z[31];
  end

  // Since riscv always return canonical NaNs (not doing NaN propagation),
  // we could preprocess operands to emulate generic fma

  return riscv_f32_mulAdd(frm, xx, y, zz);
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