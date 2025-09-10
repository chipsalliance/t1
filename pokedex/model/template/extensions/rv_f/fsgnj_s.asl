let rs1 : freg_index = UInt(GetRS1(instruction));
let rs2 : freg_index = UInt(GetRS2(instruction));
let rd : freg_index = UInt(GetRD(instruction));

// Don't write RD multiple time
var src1 : bits(32) = F[rs1];
src1[31] = F[rs2][31];

F[rd] = src1;

PC = PC + 4;
return Retired();
