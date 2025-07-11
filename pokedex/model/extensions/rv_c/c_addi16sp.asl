let nzimm : bits(6) = GetCADDI16SP_IMM(instruction);
let nzimm_10 : bits(10) = [nzimm, '0000'];
let imm : bits(32) = SignExtend(nzimm_10, 32);

if IsZero(imm) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

X[2] = X[2] + imm;

return Retired();
