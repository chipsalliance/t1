let rs1 : integer = UInt(GetRS1(instruction));
let rd  : integer = UInt(GetRD(instruction));

let addr : bits(32) = X[rs1];
let (data, result) : Result = ReadMemory(addr, 4);
if !result.is_ok then
  return result;
end

FFI_reserve_addr(addr, 4);

X[rd] = data;

return Retired();
