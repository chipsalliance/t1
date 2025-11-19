func Read_VLENB() => CsrReadResult
begin
  if !isEnabled_VS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(GetRaw_VLENB());
end

func GetRaw_VLENB() => bits(XLEN)
begin
  return (VLEN DIV 8)[31:0];
end
