func Execute_C_SWSP(instruction: bits(16)) => Result
begin
  let rs2: XRegIdx = UInt(GetCSS_RS2(instruction));
  let uimm8: bits(8) = [GetCSWSP_IMM(instruction), '00'];
  let offset: bits(XLEN) = ZeroExtend(uimm8, 32);
  let addr: bits(XLEN) = X[2] + offset;

  let result : Result = WriteMemory(addr, X[rs2]);
  if !result.is_ok then
    return result;
  end

  PC = PC + 2;
  return Retired();
end