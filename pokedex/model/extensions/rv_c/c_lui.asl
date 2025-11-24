func Execute_C_LUI(instruction: bits(16)) => Result
begin
  let rd: XRegIdx = UInt(GetRD(instruction));
  let nzimm : bits(6) = GetCI_IMM(instruction);
  let imm: bits(XLEN) = SignExtend([nzimm, Zeros(12)], XLEN);

  // FIXME : it should be unreachable after the decoder supports decoding priority
  if rd == 2 then
    return Execute_C_ADDI16SP(instruction);
  end

  if IsZero(nzimm) then
    // reserved encoding
    return IllegalInstruction();
  end

  // NOTE hint: when rd == 0, it is a hint

  X[rd] = imm;

  PC = PC + 2;
  return Retired();
end