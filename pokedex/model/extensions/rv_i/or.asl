let rs1 : integer{0..31} = UInt(GetArg_RS1(instruction));
let rs2 : integer{0..31} = UInt(GetArg_RS2(instruction));
let rd  : integer{0..31} = UInt(GetArg_RD(instruction));

X[rd] = X[rs1] OR X[rs2];

PC = PC + 4;
