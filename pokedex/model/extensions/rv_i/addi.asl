let rd  : integer{0..31} = UInt(GetArg_RD(instruction));

// NOP optimization
if rd != 0 then
  let imm : bits(32) = SignExtend(GetArg_IMM12(instruction), 32);
  let rs1 : integer{0..31} = UInt(GetArg_RS1(instruction));
  X[rd] = X[rs1] + imm;
end

PC = PC + 4;
