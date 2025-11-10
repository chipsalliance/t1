func Read_VXSAT() => CsrReadResult
begin
  if !isEnabled_VS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk([Zeros(31), VXSAT]);
end

func Write_VXSAT(value: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  // uppper bits are ignored, required by spec
  VXSAT = value[0];

  logWrite_VCSR();

  // set mstatus.vs = dirty
  makeDirty_VS();

  return Retired();
end