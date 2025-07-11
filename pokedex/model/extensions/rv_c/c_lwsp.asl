let rd : integer{0..31} = UInt(GetRD(instruction));
if rd == 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let imm : bits(6) = GetCLWSP_IMM(instruction);
let offset : bits(32) = ZeroExtend([imm, '00'], 32);

let addr : bits(32) = X[2] + offset;
let (data : bits(32), result : Result)  = ReadMemory(addr, 32);
if !result.is_ok then
  return result;
end

X[rd] = data;

PC = PC + 2;

return Retired();
