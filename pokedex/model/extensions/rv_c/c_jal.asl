func Execute_C_JAL(instruction: bits(16)) => Result
begin
  let imm: bits(11) = GetCJ_IMM(instruction);
  let offset: bits(32) = SignExtend([imm, '0'], 32);

  let next_pc : bits(XLEN) = PC + 2;

  PC = PC + offset;
  X[1] = next_pc;

  return Retired();
end
