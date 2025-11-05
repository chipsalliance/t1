func Read_MVENDORID() => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  return Ok(CFG_MVENDORID);
end
