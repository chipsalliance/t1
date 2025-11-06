func Read_VTYPE() => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  return Ok(VTYPE_BITS);
end

func GetRaw_VTYPE() => bits(32)
begin

  if VTYPE.ill then
      return ['1', Zeros(31)];
  end

  var sew_bits : bits(3);
  var lmul_bits : bits(3);

  case VTYPE.sew of
    when 8 => sew_bits = '000';
    when 16 => sew_bits = '001';
    when 32 => sew_bits = '010';
    when 64 => sew_bits = '011';
  end

  case VTYPE.lmul of
    when 0 => lmul_bits = '000';
    when 1 => lmul_bits = '001';
    when 2 => lmul_bits = '010';
    when 3 => lmul_bits = '011';
    when -1 => lmul_bits = '111';
    when -2 => lmul_bits = '110';
    when -3 => lmul_bits = '101';
  end

  return [
      Zeros(24),
      VTYPE.ma,   // [7]
      VTYPE.ta,   // [6]
      sew_bits,   // [5:3]
      lmul_bits   // [2:0]
  ];
end

// utility functions

func logWrite_VTYPE_VL()
begin
  FFI_write_CSR_hook("vtype", GetRaw_VTYPE());
  FFI_write_CSR_hook("vl", VL[31:0]);
end
