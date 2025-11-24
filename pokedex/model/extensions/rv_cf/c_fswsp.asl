func Execute_C_FSWSP(instruction: bits(16)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  let fs2: FRegIdx = UInt(GetCSS_RS2(instruction));
  let uimm8: bits(8) = [GetCSWSP_IMM(instruction), '00'];
  let offset: bits(XLEN) = ZeroExtend(uimm8, XLEN);
  let addr: bits(XLEN) = X[2] + offset;

  let result: Result = WriteMemory(addr, F[fs2]);
  if !result.is_ok then
    return result;
  end

  // no makeDirty_FS
  PC = PC + 2;
  return Retired();
end
