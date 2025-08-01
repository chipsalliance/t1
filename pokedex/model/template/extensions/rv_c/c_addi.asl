let nzimm : bits(6) = GetNZIMM(instruction);
let imm : bits(32) = SignExtend(nzimm, 32);

let rd : integer{0..31} = UInt(GetRD(instruction));

if rd != 0 && (!IsZero(nzimm)) then
  X[rd] = X[rd] + imm;
end

PC = PC + 2;

return Retired();
