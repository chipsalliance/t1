func Execute_C_ANDI(instruction: bits(16)) => Result
begin
  let rd: XRegIdx = UInt(GetCB_RS1(instruction)) + 8;
  let imm6: bits(6) = GetC_ADDI_IMM(instruction);
  let imm: bits(XLEN) = SignExtend(imm6, XLEN);

  X[rd] = X[rd] AND imm;

  PC = PC + 2;
  return Retired();
end