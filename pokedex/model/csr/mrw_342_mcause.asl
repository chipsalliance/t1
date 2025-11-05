func Read_MCAUSE() => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  return Ok([
    MCAUSE_IS_INTERRUPT_BIT,  // [31]
    MCAUSE_XCPT_CODE          // [30:0]
  ]);
end

func Write_MCAUSE(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  MCAUSE_IS_INTERRUPT_BIT = value[31];
  MCAUSE_XCPT_CODE = value[30:0];

  logWrite_MCAUSE();

  return Retired();
end

// utility functions

func logWrite_MCAUSE()
begin
  FFI_write_CSR_hook("mcause", [
    MCAUSE_IS_INTERRUPT_BIT,
    MCAUSE_XCPT_CODE
  ]);
end
