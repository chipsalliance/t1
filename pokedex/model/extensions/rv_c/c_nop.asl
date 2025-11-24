func Execute_C_NOP(instruction: bits(16)) => Result
begin
  PC = PC + 2;
  return Retired();
end
