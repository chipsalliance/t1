let rs1 : XREG_TYPE = UInt(GetRS1(instruction));
let rd : freg_index = UInt(GetRD(instruction));

F[rd] = X[rs1];

PC = PC + 4;

return Retired();
