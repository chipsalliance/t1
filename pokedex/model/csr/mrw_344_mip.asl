func Read_MIP() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  var value : bits(32) = Zeros(32);
  value[7] = getExternal_MTIP;
  value[11] = getExternal_MEIP;
  return CsrReadOk(value);
end

func GetRaw_MIP() => bits(XLEN)
begin
  // TODO: the exact semantics should be discussed later
  return Zeros(32);
end

func Write_MIP(value : bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // currently we have no writable bits,
  // thus it is no-op
  return Retired();
end
