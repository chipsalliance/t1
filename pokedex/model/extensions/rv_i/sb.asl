let imm12 : bits(12) = [GetArg_IMM12HI(instruction), GetArg_IMM12LO(instruction)];

let rs1_idx : integer{0..31} = UInt(GetArg_RS1(instruction));
let rs1   : bits(32) = X[rs1_idx];

let addr  : bits(32) = rs1 + SignExtend(imm12, 32);

let rs2_idx : integer{0..31} = UInt(GetArg_RS2(instruction));
let rs2_val : bits(32) = X[rs2_idx];

FFI_write_physical_memory_8bits(addr, rs2_val[7:0]);

PC = PC + 4;
