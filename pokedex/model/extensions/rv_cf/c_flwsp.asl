func Execute_C_FLWSP(instruction: bits(16)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  let fd: FRegIdx = UInt(GetRD(instruction));
  let uimm8: bits(8) = [GetCLWSP_IMM(instruction), '00'];
  let offset: bits(XLEN) = ZeroExtend(uimm8, XLEN);

  let addr: bits(XLEN) = X[2] + offset;
  let (data: bits(32), result: Result)  = ReadMemory(addr, 32);
  if !result.is_ok then
    return result;
  end

  F[fd] = data;

  makeDirty_FS();
  PC = PC + 2;
  return Retired();
end