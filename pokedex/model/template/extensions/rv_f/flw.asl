let rs1 : xreg_index = UInt(GetRS1(instruction));
let rd : xreg_index = UInt(GetRD(instruction));
let imm : bits(32) = SignExtend(GetIMM(instruction), 32);

let addr : bits(32) = X[rs1] + imm;
let (value : bits(32), result : Result) = ReadMemory(addr, 32);
if !result.is_ok then
  return result;
end

F[rd] = value;

PC = PC + 4;

return Retired();
