func Execute_C_ADD(instruction: bits(16)) => Result
begin
  let rd: XRegIdx = UInt(GetCR_RS1(instruction));
  let rs2: XRegIdx = UInt(GetCR_RS2(instruction));

  // FIXME : it should be unreachable after the decoder supports decoding priority
  if rs2 == 0 then
    return Execute_C_JALR(instruction);
  end

  // NOTE hint: when rd == 0, it is a hint

  X[rd] = X[rd] + X[rs2];

  PC = PC + 2;
  return Retired();
end
