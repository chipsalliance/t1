func Read_MARCHID() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(CFG_MARCHID);
end

func GetRaw_MARCHID() => bits(XLEN)
begin
  return CFG_MARCHID;
end
