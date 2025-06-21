let rd_idx : integer{0..31} = UInt(GetArg_RD(instruction));
let next_pc : bits(32) = PC + 4;

let imm12 : bits(12) = GetArg_IMM12(instruction);
let imm_value : bits(32) = SignExtend(imm12, 32);
let rs1_idx : integer{0..31} = UInt(GetArg_RS1(instruction));
PC = imm_value + X[rs1_idx];
PC[0] = '0';

X[rd_idx] = next_pc;
// TODO: return address stack handle
