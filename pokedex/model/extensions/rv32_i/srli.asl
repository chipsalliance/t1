let rs1 : integer {0..31} = UInt(GetArg_RS1(instruction));
let shift_amount : integer = UInt(GetArg_IMM5(instruction));
let rd : integer {0..31} = UInt(GetArg_RD(instruction));
// ShiftRightLogical is built-in
X[rd] = ShiftRightLogical(X[rs1], shift_amount);

PC = PC + 4;
