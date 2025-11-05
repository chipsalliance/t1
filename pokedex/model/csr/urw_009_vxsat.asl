func Read_VXSAT() => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  return Ok([Zeros(31), VXSAT]);
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