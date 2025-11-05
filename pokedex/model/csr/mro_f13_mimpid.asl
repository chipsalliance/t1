func Read_MIMPID() => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  return Ok(CFG_MIMPID);
end
