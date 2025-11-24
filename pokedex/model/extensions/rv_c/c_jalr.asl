func Execute_C_JALR(instruction: bits(16)) => Result
begin
  let rs1: XRegIdx = UInt(GetCR_RS1(instruction));

  // FIXME : it should be unreachable after the decoder supports decoding priority
  if rs1 == 0 then
    return Execute_C_EBREAK(instruction);
  end

  let next_pc : bits(XLEN) = PC + 2;

  PC = X[rs1];
  X[1] = next_pc;

  return Retired();
end