func Read_MIP() => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  var value : bits(32) = Zeros(32);
  value[7] = getExternal_MTIP;
  value[11] = getExternal_MEIP;
  return Ok(value);
end

func Write_MIP(value : bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // currently we have no writable bits,
  // thus it is no-op
  return Retired();
end
