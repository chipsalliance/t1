let imm : bits(20) = GetUIMM(instruction);
let rd  : integer{0..31}  = UInt(GetRD(instruction));

X[rd] = [imm, Zeros(12)];

PC = PC + 4;

return Retired();
