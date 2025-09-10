let rd : freg_index = UInt(GetRD(instruction));
let imm : bits(6) = GetCLWSP_IMM(instruction);
let offset : bits(32) = ZeroExtend([imm, '00'], 32);

let addr : bits(32) = X[2] + offset;
let (data : bits(32), result : Result)  = ReadMemory(addr, 32);
if !result.is_ok then
  return result;
end

F[rd] = data;

PC = PC + 2;

return Retired();
