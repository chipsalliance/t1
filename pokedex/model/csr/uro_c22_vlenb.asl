func Read_VLENB() => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  let vlenb = VLEN DIV 8;

  return Ok(vlenb[31:0]);
end
