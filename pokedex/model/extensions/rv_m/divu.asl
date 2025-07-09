let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let rd  : integer{0..31} = UInt(GetRD(instruction));

let src1 : bits(32) = X[rs1];
let src2 : bits(32) = X[rs2];

if IsZero(src2) then
  X[rd] = Ones(32);
else
  // Division: rount to zero
  let dst : integer = UInt(src1) QUOT UInt(src2);
  X[rd] = dst[31:0];
end

PC = PC + 4;

return Retired();
