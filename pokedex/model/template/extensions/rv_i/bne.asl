let offset : bits(13) = [GetBIMM(instruction), '0'];

let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rs2 : integer{0..31} = UInt(GetRS2(instruction));

if X[rs1] != X[rs2] then
  let target : bits(32) = PC + SignExtend(offset, 32);
  PC = target;
else
  PC = PC + 4;
end

return Retired();
