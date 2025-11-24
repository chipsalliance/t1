func Execute_C_SLLI(instruction: bits(16)) => Result
begin
  let rd: XRegIdx = UInt(GetRD(instruction));
  let shamt: bits(6) = GetC_SHAMT(instruction);

  if shamt[5] != '0' then
    return IllegalInstruction();
  end

  // NOTE hint: when rd == 0 || shamt == 0, it is a hint

  X[rd] = ShiftLeft(X[rd], UInt(shamt));

  PC = PC + 2;
  return Retired();
end
