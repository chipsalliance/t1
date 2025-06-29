let rd  : integer{0..31} = UInt(GetRD(instruction));

// NOP optimization
if rd != 0 then
  let imm : bits(12) = GetIMM(instruction);
  let rs1 : integer{0..31} = UInt(GetRS1(instruction));
  X[rd] = X[rs1] + SignExtend(imm, 32);
end

PC = PC + 4;

return Retired();
