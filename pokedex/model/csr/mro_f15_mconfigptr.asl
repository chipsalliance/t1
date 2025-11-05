func Read_MCONFIGPTR() => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  return Ok(CFG_MCONFIGPTR);
end
