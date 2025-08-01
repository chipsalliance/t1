let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let rd  : integer{0..31} = UInt(GetRD(instruction));

let src2 : bits(32) = X[rs2];
let shift_amount : integer = UInt(src2[4:0]);

X[rd] = ShiftRightArithmetic(X[rs1], shift_amount);

PC = PC + 4;

return Retired();
