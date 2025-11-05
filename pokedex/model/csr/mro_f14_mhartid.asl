func Read_MHARTID() => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  return Ok(CFG_MHARTID);
end
