let rs1 : integer{0..31} = UInt(GetCR_RS1(instruction));
if rs1 == 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

PC = X[rs1];

return Retired();
