let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let rd : integer{0..31} = UInt(GetRD(instruction));

let signed_rs1 : bits(64) = SignExtend(X[rs1], 64);
let unsigned_rs2 : bits(64) = ZeroExtend(X[rs2], 64);
let result : bits(64) = signed_rs1 * unsigned_rs2;

X[rd] = result[63:32];

PC = PC + 4;

return Retired();
