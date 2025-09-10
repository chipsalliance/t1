let rs1 : freg_index = UInt(GetRS1(instruction));
let rd : XREG_TYPE = UInt(GetRD(instruction));

X[rd] = F[rs1];

PC = PC + 4;

return Retired();
