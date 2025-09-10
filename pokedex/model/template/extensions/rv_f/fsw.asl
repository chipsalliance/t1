let imm : bits(32) = SignExtend(GetSIMM(instruction), 32);
let rs1 : XREG_TYPE = UInt(GetRS1(instruction));
let rs2 : freg_index = UInt(GetRS2(instruction));

let addr : bits(32) = X[rs1] + imm;
let result : Result = WriteMemory(addr, F[rs2]);
if !result.is_ok then
  return result;
end

PC = PC + 4;

return Retired();
