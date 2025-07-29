let imm : bits(8) = GetCIW_IMM(instruction);
let imm_ext : bits(32) = ZeroExtend([imm, '00'], 32);

if IsZero(imm_ext) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let rd : integer{8..15} = UInt(GetCIW_RD(instruction)) + 8;
X[rd] = X[2] + imm_ext;

PC = PC + 2;

return Retired();
