let rs1 : integer{8..15} = UInt(GetCS_RS1(instruction)) + 8;
let rs2 : integer{8..15} = UInt(GetCS_RS2(instruction)) + 8;
let imm : bits(5) = GetCSW_IMM(instruction);
let offset : bits(32) = ZeroExtend([imm, '00'], 32);
let addr : bits(32) = X[rs1] + offset;

let result : Result = WriteMemory(addr, F[rs2]);
if !result.is_ok then
  return result;
end

PC = PC + 2;

return Retired();
