let rd_idx : integer{0..31}  = UInt(GetArg_RD(instruction));
if rd_idx == 0 then
  // TODO: raise exception
  // No return here because specification said that:
  //
  // > Loads with a destination of x0 must still raise any exceptions and
  // > cause any other side effects even though the load value is discarded.
end

let offset : bits(32) = SignExtend(GetArg_IMM12(instruction), 32);
let rs1_idx : integer{0..31} = UInt(GetArg_RS1(instruction));
let rs1    : bits(32) = X[rs1_idx];
let addr   : bits(32) = rs1 + offset;
// Load 4 byte memory
let value  : bits(32) = FFI_read_physical_memory_32bits(addr);
X[rd_idx] = value;
PC = PC + 4;
