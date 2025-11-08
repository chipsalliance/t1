func Execute_FLW(instruction: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  let fd : FRegIdx = UInt(GetRD(instruction));
  let rs1 : XRegIdx = UInt(GetRS1(instruction));
  let imm12 : bits(12) = GetIMM(instruction);

  let addr : bits(XLEN) = X[rs1] + SignExtend(imm12, XLEN);
  let (value : bits(32), result : Result) = ReadMemory(addr, 32);
  if !result.is_ok then
    return result;
  end

  F[fd] = value;

  makeDirty_FS();
  PC = PC + 4;
  return Retired();
end