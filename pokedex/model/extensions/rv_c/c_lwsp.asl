func Execute_C_LWSP(instruction: bits(16)) => Result
begin
  let rd: XRegIdx = UInt(GetRD(instruction));
  let uimm8: bits(8) = [GetCLWSP_IMM(instruction), '00'];
  let offset: bits(XLEN) = ZeroExtend(uimm8, XLEN);

  if rd == 0 then
    // reserved encoding
    return IllegalInstruction();
  end

  let addr: bits(XLEN) = X[2] + offset;
  let (data: bits(32), result: Result)  = ReadMemory(addr, 32);
  if !result.is_ok then
    return result;
  end

  X[rd] = data;

  PC = PC + 2;
  return Retired();
end
