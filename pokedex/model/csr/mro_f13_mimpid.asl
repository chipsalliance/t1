func Read_MIMPID() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(CFG_MIMPID);
end

func GetRaw_MIMPID() => bits(XLEN)
begin
  return CFG_MIMPID;
end
