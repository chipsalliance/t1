let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let rd  : integer{0..31} = UInt(GetRD(instruction));

let src1 : bits(32) = X[rs1];
let src2 : bits(32) = X[rs2];

let most_negative : bits(32) = ['1', Zeros(31)];

// division by zero
if IsZero(src2) then
  X[rd] = X[rs1];
// overflow
elsif src1 == most_negative && IsOnes(src2) then
  X[rd] = Zeros(32);
else
  // Division: rount to zero
  let dst : integer = SInt(src1) REM SInt(src2);
  X[rd] = dst[31:0];
end

PC = PC + 4;

return Retired();
