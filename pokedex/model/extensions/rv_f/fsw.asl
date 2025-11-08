func Execute_FSW(instruction: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  let rs1 : XRegIdx = UInt(GetRS1(instruction));
  let fs2 : FRegIdx = UInt(GetRS2(instruction));
  let imm12 : bits(12) = GetSIMM(instruction);

  let addr : bits(XLEN) = X[rs1] + SignExtend(imm12, XLEN);

  let result : Result = WriteMemory(addr, F[fs2]);
  if !result.is_ok then
    return result;
  end

  // no makeDirty_FS
  PC = PC + 4;
  return Retired();
end
