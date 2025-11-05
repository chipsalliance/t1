func Read_VCSR() => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  return Ok(GetRaw_VCSR());
end

func Write_VCSR(value: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  // uppper bits are ignored, required by spec
  VXRM = value[2:1];
  VXSAT = value[0];

  logWrite_VCSR();

  // set mstatus.vs = dirty
  makeDirty_VS();

  return Retired();
end

// utility functions

func GetRaw_VCSR() => bits(32)
begin
  return [
    Zeros(29),
    VXRM,   // [2:1]
    VXSAT   // [0]
  ];
end

func logWrite_VCSR()
begin
  FFI_write_CSR_hook("vcsr", GetRaw_VCSR());
end
