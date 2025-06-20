let imm20  : bits(20) = GetArg_IMM20(instruction);
let offset : bits(32) = [imm20, Zeros(12)];
let rd     : integer{0..31}  = UInt(GetArg_RD(instruction));
X[rd] = PC + offset;
PC = PC + 4;
