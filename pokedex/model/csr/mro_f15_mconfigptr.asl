func Read_MCONFIGPTR() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(CFG_MCONFIGPTR);
end

func GetRaw_MCONFIGPTR() => bits(XLEN)
begin
  return CFG_MCONFIGPTR;
end
