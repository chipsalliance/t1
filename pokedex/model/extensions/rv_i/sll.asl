let rs1 : integer{0..31} = UInt(GetArg_RS1(instruction));
let rs2 : integer{0..31} = UInt(GetArg_RS2(instruction));
let rd  : integer{0..31} = UInt(GetArg_RD(instruction));

let rs2_val : bits(32) = X[rs2];
let shift_amount : integer = UInt(rs2_val[4:0]);

X[rd] = ShiftLeft(X[rs1], shift_amount);

PC = PC + 4;
