func Execute_C_J(instruction: bits(16)) => Result
begin
  let imm: bits(11) = GetCJ_IMM(instruction);
  let offset: bits(XLEN) = SignExtend([imm, '0'], XLEN);

  PC = PC + offset;
  return Retired();
end