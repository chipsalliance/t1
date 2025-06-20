let rs1 : integer{0..31} = UInt(GetArg_RS1(instruction));
let rs2 : integer{0..31} = UInt(GetArg_RS2(instruction));
let rd  : integer{0..31} = UInt(GetArg_RD(instruction));

let src1 : integer = UInt(X[rs1]);
let src2 : integer = UInt(X[rs2]);

if src1 < src2 then
  X[rd] = ZeroExtend('0001', 32);
else
  X[rd] = Zeros(32);
end

PC = PC + 4;
