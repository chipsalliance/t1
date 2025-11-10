func Read_VLENB() => CsrReadResult
begin
  if !isEnabled_VS() then
    return CsrReadIllegalInstruction();
  end

  let vlenb = VLEN DIV 8;

  return CsrReadOk(vlenb[31:0]);
end
