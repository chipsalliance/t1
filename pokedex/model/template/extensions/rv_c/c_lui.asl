let nzimm : bits(6) = GetCI_IMM(instruction);
let imm : bits(32) = SignExtend([nzimm, Zeros(12)], 32);

if IsZero(imm) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let rd : integer{0..31} = UInt(GetRD(instruction));

if rd == 0 then
  // This is reserved encoding
  return IllegalInstruction();
end

// FIXME : it should be unreachable after the decoder supports decoding priority
if rd == 2 then
  return Execute_C_ADDI16SP(instruction);
end

X[rd] = imm;

PC = PC + 2;

return Retired();
