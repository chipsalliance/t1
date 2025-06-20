let imm : bits(32) = SignExtend(GetArg_IMM12(instruction), 32);
let rs1 : integer{0..31} = UInt(GetArg_RS1(instruction));
let rd  : integer{0..31} = UInt(GetArg_RD(instruction));

X[rd] = X[rs1] OR imm;
PC = PC + 4;
