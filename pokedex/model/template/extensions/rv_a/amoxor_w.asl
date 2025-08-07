let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rs2 : integer{0..31} = UInt(GetRS2(instruction));
let rd : integer{0..31} = UInt(GetRD(instruction));
let is_aq : boolean = IsAcquire(instruction);
let is_rl : boolean = IsRelease(instruction);

let addr : bits(32) = X[rs1];
if (!IsZero(addr[1:0])) then
  return Exception(CAUSE_STORE_ACCESS, addr);
end

let result: FFI_ReadResult(32) = FFI_amo(AMO_XOR, X[rs1], X[rs2], is_aq, is_rl);
if !result.success then
  return Exception(CAUSE_STORE_ACCESS, addr);
end

X[rd] = result.data;

PC = PC + 4;

return Retired();
