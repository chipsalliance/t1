let rd : integer{8..15} = UInt(GetCA_RS1(instruction)) + 8;
let rs2 : integer{8..15} = UInt(GetCA_RS2(instruction)) + 8;

X[rd] = X[rd] OR X[rs2];

PC = PC + 2;

return Retired();
