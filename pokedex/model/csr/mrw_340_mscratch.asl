func Read_MSCRATCH() => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  return Ok(MSCRATCH);
end

func Write_MSCRATCH(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  MSCRATCH = value;

  // This is the only place that modifies mscratch
  FFI_write_CSR_hook("mscratch", value);

  return Retired();
end
