let imm : bits(20) = GetArg_IMM20(instruction);
let rd  : integer{0..31}  = UInt(GetArg_RD(instruction));

X[rd] = [imm, Zeros(12)];

PC = PC + 4;
