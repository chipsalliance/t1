let nzimm : bits(6) = GetCI_IMM(instruction);
let imm : bits(32) = SignExtend([nzimm, Zeros(12)], 32);

if IsZero(imm) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let rd : integer{0..31} = UInt(GetRD(instruction));
if rd == 0 || rd == 2 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

X[rd] = imm;

PC = PC + 2;

return Retired();
