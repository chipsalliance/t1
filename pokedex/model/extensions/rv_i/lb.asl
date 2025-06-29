let rd : integer{0..31}  = UInt(GetRD(instruction));

let imm : bits(12) = GetIMM(instruction);
let offset : bits(32) = SignExtend(imm, 32);

let rs1 : integer{0..31} = UInt(GetRS1(instruction));

let addr : bits(32) = X[rs1] + offset;

// Load 1 byte memory
let (data, result) = ReadMemory(addr, 8);
if !result.is_ok then
  return result;
end

let value  : bits(32) = SignExtend(data, 32);
X[rd] = value;
PC = PC + 4;

return Retired();
