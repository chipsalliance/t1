let rd : integer{0..31} = UInt(GetRD(instruction));
let next_pc : bits(32) = PC + 4;

let imm : bits(12) = GetIMM(instruction);
let offset : bits(32) = SignExtend(imm, 32);

let rs1 : integer{0..31} = UInt(GetRS1(instruction));

var target : bits(32) = offset + X[rs1];
target[0] = '0';
if target[1] != '0' then
  return Exception(CAUSE_MISALIGNED_FETCH, target);
end

PC = target;

X[rd] = next_pc;
// TODO: return address stack handle

return Retired();
