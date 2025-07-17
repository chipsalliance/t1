let offset : bits(13) = [GetBIMM(instruction), '0'];

let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let src1 : integer = SInt(X[rs1]);

let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let src2 : integer = SInt(X[rs2]);

if src1 >= src2 then
  let target : bits(32) = PC + SignExtend(offset, 32);
  PC = target;
else
  PC = PC + 4;
end

return Retired();
