func Read_MARCHID() => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  return Ok(CFG_MARCHID);
end
