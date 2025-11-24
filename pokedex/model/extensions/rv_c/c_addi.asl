func Execute_C_ADDI(instruction: bits(16)) => Result
begin
  let rd: XRegIdx = UInt(GetRD(instruction));
  let nzimm: bits(6) = GetNZIMM(instruction);
  let imm: bits(XLEN) = SignExtend(nzimm, XLEN);

  // NOTE hint: when rd == 0 && imm == 0, it is c.nop
  // NOTE hint: when rd == 0 || imm == 0, it is a hint

  X[rd] = X[rd] + imm;

  PC = PC + 2;
  return Retired();
end
