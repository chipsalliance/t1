func Execute_C_ADDI4SPN(instruction: bits(16)) => Result
begin
  let rd: XRegIdx = UInt(GetCIW_RD(instruction)) + 8;
  let uimm10: bits(10) = [GetCIW_IMM(instruction), '00'];
  let imm: bits(XLEN) = ZeroExtend(uimm10, XLEN);

  if IsZero(uimm10) then
    // when uimm10 == 0, it is c.unimp
    // reserved encoding
    return IllegalInstruction();
  end

  
  X[rd] = X[2] + imm;

  PC = PC + 2;
  return Retired();
end