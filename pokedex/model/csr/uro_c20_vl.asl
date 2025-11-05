func Read_VL() => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  return Ok(VL[31:0]);
end
