let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let rd  : integer{0..31} = UInt(GetRD(instruction));

let addr : bits(32) = X[rs1];
if !IsZero(addr[1:0]) then
  return Exception(CAUSE_MISALIGNED_STORE, addr);
end

if FFI_store_conditional(addr, X[rs2]) then
  X[rd] = 0[31:0];
else
  X[rd] = 1[31:0];
end

PC = PC + 4;

return Retired();
