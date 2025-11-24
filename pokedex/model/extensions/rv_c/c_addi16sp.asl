func Execute_C_ADDI16SP(instruction: bits(16)) => Result
begin
  let nzimm: bits(6) = GetCADDI16SP_IMM(instruction);
  let imm: bits(XLEN) = SignExtend([nzimm, '0000'], XLEN);

  if IsZero(nzimm) then
    // reserved encoding
    return IllegalInstruction();
  end

  X[2] = X[2] + imm;

  PC = PC + 2;
  return Retired();
end