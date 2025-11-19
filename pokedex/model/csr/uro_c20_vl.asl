func Read_VL() => CsrReadResult
begin
  if !isEnabled_VS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(VL[31:0]);
end

func GetRaw_VL() => bits(XLEN)
begin
  return VL[31:0];
end
