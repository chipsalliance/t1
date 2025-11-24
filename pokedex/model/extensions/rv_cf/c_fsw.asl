func Execute_C_FSW(instruction: bits(16)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  let rs1: XRegIdx = UInt(GetCS_RS1(instruction)) + 8;
  let fs2: FRegIdx = UInt(GetCS_RS2(instruction)) + 8;
  let uimm7: bits(7) = [GetCSW_IMM(instruction), '00'];
  let offset: bits(32) = ZeroExtend(uimm7, 32);

  let addr: bits(XLEN) = X[rs1] + offset;
  let result: Result = WriteMemory(addr, F[fs2]);
  if !result.is_ok then
    return result;
  end

  // no makeDirty_FS
  PC = PC + 2;
  return Retired();
end
