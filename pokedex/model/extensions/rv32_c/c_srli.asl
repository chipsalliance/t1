let shamt : bits(6) = GetC_SHAMT(instruction);
if shamt[5] != '0' then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let rs1 : integer{8..15} = UInt(GetCB_RS1(instruction)) + 8;
let rd = rs1;
X[rd] = ShiftRightLogical(X[rs1], UInt(shamt));
PC = PC + 2;

return Retired();
