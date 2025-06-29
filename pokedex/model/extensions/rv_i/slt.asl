let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let rd  : integer{0..31} = UInt(GetRD(instruction));
let src1 : integer = SInt(X[rs1]);
let src2 : integer = SInt(X[rs2]);

if src1 < src2 then
  X[rd] = ZeroExtend('0001', 32);
else
  X[rd] = Zeros(32);
end

PC = PC + 4;

return Retired();
