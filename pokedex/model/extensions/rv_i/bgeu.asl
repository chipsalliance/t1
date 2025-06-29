let offset : bits(13) = [GetBIMM(instruction), '0'];

let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let src1 : integer = UInt(X[rs1]);

let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let src2 : integer = UInt(X[rs2]);

if src1 >= src2 then
  let target : bits(32) = PC + SignExtend(offset, 32);
  if target[1] != '0' then
    return Exception(CAUSE_MISALIGNED_FETCH, target);
  end

  PC = target;
else
  PC = PC + 4;
end

return Retired();
