let rd : integer{0..31} = UInt(GetCR_RS1(instruction));
let rs2 : integer{0..31} = UInt(GetCR_RS2(instruction));

if rs2 == 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

if rd != 0 then
  X[rd] = X[rd] + X[rs2];
end

PC = PC + 2;
return Retired();
