let rd_idx : integer{0..31}  = UInt(GetArg_RD(instruction));
let offset : bits(32) = SignExtend(GetArg_IMM12(instruction), 32);
let rs1_idx : integer{0..31} = UInt(GetArg_RS1(instruction));
let rs1    : bits(32) = X[rs1_idx];
let addr   : bits(32) = rs1 + offset;
// Load 2 byte memory
let value  : bits(32) = SignExtend(FFI_read_physical_memory_16bits(addr), 32);
X[rd_idx] = value;
PC = PC + 4;
