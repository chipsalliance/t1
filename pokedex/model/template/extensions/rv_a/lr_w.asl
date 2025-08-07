let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rd  : integer{0..31} = UInt(GetRD(instruction));

let addr : bits(32) = X[rs1];
if addr[1:0] != '00' then
  return Exception(CAUSE_MISALIGNED_LOAD, addr);
end

let result = FFI_load_reserved(addr);
if !result.success then
  return Exception(CAUSE_LOAD_ACCESS, addr);
end

X[rd] = SignExtend(result.data, 32);

PC = PC + 4;

return Retired();
