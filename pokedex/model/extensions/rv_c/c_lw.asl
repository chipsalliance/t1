let rd : integer{8..15} = UInt(GetCL_RD(instruction)) + 8;
let imm : bits(5) = GetCLW_IMM(instruction);
let offset : bits(32) = ZeroExtend([imm, '00'], 32);
let rs1 : integer{8..15} = UInt(GetCL_RS1(instruction)) + 8;

let addr : bits(32) = X[rs1] + offset;
let (data : bits(32), result : Result)  = ReadMemory(addr, 32);
if !result.is_ok then
  return result;
end

X[rd] = data;

PC = PC + 2;

return Retired();
