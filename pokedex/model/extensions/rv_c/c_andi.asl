let imm : bits(6) = GetC_ADDI_IMM(instruction);
let rs1 : integer{8..15} = UInt(GetCB_RS1(instruction)) + 8;
let rd = rs1;

X[rd] = X[rs1] AND SignExtend(imm, 32);

PC = PC + 2;

return Retired();
