let rs1 : integer{0..31} = UInt(GetArg_RS1(instruction));
let rs2 : integer{0..31} = UInt(GetArg_RS2(instruction));
let rd  : integer{0..31} = UInt(GetArg_RD(instruction));

let shift_amount : integer = UInt(X[rs2]);

X[rd] = ShiftRightLogical(X[rs1], shift_amount);

PC = PC + 4;
