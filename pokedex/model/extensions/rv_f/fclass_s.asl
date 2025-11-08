func Execute_FCLASS_S(instruction: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  let rd : XRegIdx = UInt(GetRD(instruction));
  let fs1 : FRegIdx = UInt(GetRS1(instruction));

  var mask : bits(10) = riscv_fclass_f32(F[fs1]);

  X[rd] = ZeroExtend(mask, XLEN);

  // no makeDirty_FS
  PC = PC + 4;
  return Retired();
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
