func Read_MSTATUSH() => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  return Ok(Zeros(32));
end

func Write_MSTATUSH(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // no-op
  return Retired();
end
