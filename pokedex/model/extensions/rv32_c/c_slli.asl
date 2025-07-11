let shamt : bits(6) = GetC_SHAMT(instruction);
if shamt[5] != '0' then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let rd : integer{0..31} = UInt(GetRD(instruction));
if rd != 0 && !IsZero(shamt) then
  X[rd] = ShiftLeft(X[rd], UInt(shamt));
end

PC = PC + 2;
return Retired();
