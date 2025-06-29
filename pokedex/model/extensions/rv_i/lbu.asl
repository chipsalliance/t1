let rd_idx : integer{0..31}  = UInt(GetArg_RD(instruction));
let offset  : bits(32) = SignExtend(GetArg_IMM12(instruction), 32);
let rs1_idx : integer{0..31} = UInt(GetArg_RS1(instruction));
let rs1     : bits(32) = X[rs1_idx];
let addr    : bits(32) = rs1 + offset;
// Load 1 byte memory
let (data, result) = ReadMemory(addr, 8);
if !result.is_ok then
  return result;
end

let value  : bits(32) = ZeroExtend(data, 32);
X[rd_idx] = value;
PC = PC + 4;

return Retired();
