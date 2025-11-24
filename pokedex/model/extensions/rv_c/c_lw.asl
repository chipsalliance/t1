func Execute_C_LW(instruction: bits(16)) => Result
begin
  let rd: XRegIdx = UInt(GetCL_RD(instruction)) + 8;
  let rs1: XRegIdx = UInt(GetCL_RS1(instruction)) + 8;
  let uimm7: bits(7) = [GetCLW_IMM(instruction), '00'];
  let offset: bits(XLEN) = ZeroExtend(uimm7, 32);

  let addr: bits(XLEN) = X[rs1] + offset;
  let (data: bits(32), result: Result)  = ReadMemory(addr, 32);
  if !result.is_ok then
    return result;
  end

  X[rd] = data;

  PC = PC + 2;
  return Retired();
end
