func Execute_C_LI(instruction: bits(16)) => Result
begin
  let rd: XRegIdx = UInt(GetRD(instruction));
  let imm: bits(6) = GetCI_IMM(instruction);
  let value: bits(XLEN) = SignExtend(imm, XLEN);

  // NOTE hint: when rd == 0, it is a hint

  X[rd] = value;

  PC = PC + 2;
  return Retired();
end
