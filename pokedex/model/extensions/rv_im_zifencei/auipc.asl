func Execute_AUIPC(instruction: bits(32)) => Result
begin
  let imm    : bits(20) = GetUIMM(instruction);
  let offset : bits(32) = [imm, Zeros(12)];
  let rd     : integer{0..31}  = UInt(GetRD(instruction));

  X[rd] = PC + offset;

  PC = PC + 4;
  return Retired();
end
