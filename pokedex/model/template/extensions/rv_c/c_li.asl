let rd : integer{0..31} = UInt(GetRD(instruction));

if rd != 0 then
  let imm : bits(6) = GetCI_IMM(instruction);
  let val : bits(32) = SignExtend(imm, 32);
  X[rd] = val;
end

PC = PC + 2;

return Retired();
