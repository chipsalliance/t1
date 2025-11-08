func Execute_LUI(instruction: bits(32)) => Result
begin
  let rd: XRegIdx = UInt(GetRD(instruction));
  let imm: bits(20) = GetUIMM(instruction);

  X[rd] = [imm, Zeros(12)];

  PC = PC + 4;

  return Retired();
end
