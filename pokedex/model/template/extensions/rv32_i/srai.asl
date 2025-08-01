let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let imm : bits(12) = GetIMM(instruction);
let shift_amount : integer = UInt(imm[4:0]);
let rd : integer{0..31} = UInt(GetRD(instruction));
// ShiftRightArithmetic is built-in
X[rd] = ShiftRightArithmetic(X[rs1], shift_amount);

PC = PC + 4;

return Retired();
