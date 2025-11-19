func Read_MSTATUSH() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(Zeros(32));
end

func GetRaw_MSTATUSH() => bits(XLEN)
begin
  return Zeros(32);
end

func Write_MSTATUSH(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // no-op
  return Retired();
end
