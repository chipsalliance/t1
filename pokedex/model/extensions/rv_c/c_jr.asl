func Execute_C_JR(instruction: bits(16)) => Result
begin
  let rs1: XRegIdx = UInt(GetCR_RS1(instruction));
  if rs1 == 0 then
    // reserved encoding
    return IllegalInstruction();
  end

  PC = X[rs1];
  return Retired();
end