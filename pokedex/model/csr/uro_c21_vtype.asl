func Read_VTYPE() => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  return Ok(VTYPE_BITS);
end
