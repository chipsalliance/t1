let imm : bits(11) = GetCJ_IMM(instruction);
let offset : bits(32) = SignExtend([imm, '0'], 32);

PC = PC + offset;

return Retired();
