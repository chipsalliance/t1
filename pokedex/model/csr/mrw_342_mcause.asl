func Read_MCAUSE() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(MCAUSE);
end

func Write_MCAUSE(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // NOTE : impl defiend behavior
  //
  // MCAUSE is WLRL, only guaranteed to hold all supported causes
  // Here treats it as full XLEN-bit register

  MCAUSE = value;

  logWrite_MCAUSE();

  return Retired();
end

// utility functions

func logWrite_MCAUSE()
begin
  FFI_write_CSR_hook("mcause", MCAUSE);
end
