func Read_MEPC() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(MEPC);
end

func Write_MEPC(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // ext C is always enabled,
  // the least bit must be masked in writing
  MEPC = [value[31:1], '0'];

  logWrite_MEPC();

  return Retired();
end

// utility functions

func logWrite_MEPC()
begin
  FFI_write_CSR_hook("mepc", MEPC);
end
