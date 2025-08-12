let rs1 : integer = UInt(GetRS1(instruction));
let rs2 : integer = UInt(GetRS2(instruction));
let rd  : integer = UInt(GetRD(instruction));

let addr : bits(32) = X[rs1];
if FFI_is_addr_reserved(addr) then
  X[rd] = 0;
end
