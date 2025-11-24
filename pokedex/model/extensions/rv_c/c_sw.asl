func Execute_C_SW(instruction: bits(16)) => Result
begin
  let rs1: XRegIdx = UInt(GetCS_RS1(instruction)) + 8;
  let rs2: XRegIdx = UInt(GetCS_RS2(instruction)) + 8;
  let uimm7: bits(7) = [GetCSW_IMM(instruction), '00'];
  let offset: bits(XLEN) = ZeroExtend(uimm7, XLEN);
  let addr: bits(XLEN) = X[rs1] + offset;

  let result : Result = WriteMemory(addr, X[rs2]);
  if !result.is_ok then
    return result;
  end

  PC = PC + 2;
  return Retired();
end
