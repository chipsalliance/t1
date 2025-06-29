let imm : bits(12) = GetSIMM(instruction);

let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let src1 : bits(32) = X[rs1];

let addr : bits(32) = src1 + SignExtend(imm, 32);

let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let src2 : bits(32) = X[rs2];

let result : Result = WriteMemory(addr, src2[7:0]);
if !result.is_ok then
  return result;
end

PC = PC + 4;

return Retired();
