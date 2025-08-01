let rs2 : integer{0..31} = UInt(GetCSS_RS2(instruction));
let imm : bits(6) = GetCSWSP_IMM(instruction);
let offset : bits(32) = ZeroExtend([imm, '00'], 32);
let addr : bits(32) = X[2] + offset;

let result : Result = WriteMemory(addr, X[rs2]);
if !result.is_ok then
  return result;
end

PC = PC + 2;

return Retired();
