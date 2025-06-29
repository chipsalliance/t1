let rd_idx : integer{0..31} = UInt(GetArg_RD(instruction));
let next_pc : bits(32) = PC + 4;

let imm12 : bits(12) = GetArg_IMM12(instruction);
let imm_value : bits(32) = SignExtend(imm12, 32);
let rs1_idx : integer{0..31} = UInt(GetArg_RS1(instruction));

let target : bits(32) = imm_value + X[rs1_idx];
if target[1:0] != '00' then
  return Exception(CAUSE_MISALIGNED_FETCH, target);
end

PC = target;
PC[0] = '0';

X[rd_idx] = next_pc;
// TODO: return address stack handle

return Retired();
