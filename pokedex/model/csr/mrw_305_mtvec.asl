func Read_MTVEC() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk([
    MTVEC_BASE,       // [31:2]
    MTVEC_MODE_BITS   // [1:0]
  ]);
end

func Write_MTVEC(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // NOTE : impl defined behavior
  //
  // mtvec is WARL, we ignore the whole write for unsupported mode
  // '00' (direct) and '01' (vectored) are supported mode

  if value[1] == '0' then
    MTVEC_BASE = value[31:2];
    MTVEC_MODE_BITS = value[1:0];

    // This is the only place that modifies mtvec
    FFI_write_CSR_hook("mtvec", value);
  end

  return Retired();
end
