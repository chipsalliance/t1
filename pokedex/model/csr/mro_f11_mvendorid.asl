func Read_MVENDORID() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(CFG_MVENDORID);
end

func GetRaw_MVENDORID() => bits(XLEN)
begin
  return CFG_MVENDORID;
end
