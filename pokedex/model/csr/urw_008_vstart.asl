func Read_VSTART() => Result
begin
  return Ok(VSTART[31:0]);
end

func Write_VSTART(value: bits(32)) => Result
begin
  VSTART = value[LOG2_VLEN-1:0];

  // TODO : log write

  return Retired();
end
