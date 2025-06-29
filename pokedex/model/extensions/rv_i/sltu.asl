let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let rd  : integer{0..31} = UInt(GetRD(instruction));

let src1 : integer = UInt(X[rs1]);
let src2 : integer = UInt(X[rs2]);

if src1 < src2 then
  // convert integer 1 to 32-bits bit vector
  X[rd] = asl_cvt_int_bits(1, 32);
else
  X[rd] = Zeros(32);
end

PC = PC + 4;

return Retired();
