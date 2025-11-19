func Read_MHARTID() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(CFG_MHARTID);
end

func GetRaw_MHARTID() => bits(XLEN)
begin
  return CFG_MHARTID;
end
