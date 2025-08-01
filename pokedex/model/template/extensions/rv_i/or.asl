let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let rd  : integer{0..31} = UInt(GetRD(instruction));

X[rd] = X[rs1] OR X[rs2];

PC = PC + 4;

return Retired();
