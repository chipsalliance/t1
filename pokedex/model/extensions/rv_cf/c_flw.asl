func Execute_C_FLW(instruction: bits(16)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  let fd: FRegIdx = UInt(GetCL_RD(instruction)) + 8;
  let rs1: XRegIdx = UInt(GetCL_RS1(instruction)) + 8;
  let uimm7: bits(7) = [GetCLW_IMM(instruction), '00'];
  let offset: bits(32) = ZeroExtend(uimm7, 32);
  
  let addr: bits(XLEN) = X[rs1] + offset;
  let (data: bits(32), result: Result) = ReadMemory(addr, 32);
  if !result.is_ok then
    return result;
  end

  F[fd] = data;

  makeDirty_FS();
  PC = PC + 2;
  return Retired();
end