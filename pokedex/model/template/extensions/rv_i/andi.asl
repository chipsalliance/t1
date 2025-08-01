let imm : bits(12) = GetIMM(instruction);
let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rd  : integer{0..31} = UInt(GetRD(instruction));

X[rd] = X[rs1] AND SignExtend(imm, 32);
PC = PC + 4;

return Retired();
