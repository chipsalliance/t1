func Read_VXRM() => CsrReadResult
begin
  if !isEnabled_VS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk([Zeros(30), VXRM]);
end

func Write_VXRM(value: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  // uppper bits are ignored, required by spec
  VXRM = value[1:0];

  logWrite_VCSR();

  // set mstatus.vs = dirty
  makeDirty_VS();

  return Retired();
end
