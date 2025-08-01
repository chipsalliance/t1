let imm : bits(8) = GetCB_IMM(instruction);
let offset : bits(32) = SignExtend([imm, '0'], 32);
let rs1 : integer{8..15} = UInt(GetCB_RS1(instruction)) + 8;

if IsZero(X[rs1]) then
  PC = PC + offset;
else
  PC = PC + 2;
end

return Retired();
