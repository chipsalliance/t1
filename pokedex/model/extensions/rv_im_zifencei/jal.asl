func Execute_JAL(instruction: bits(32)) => Result
begin
  let rd : integer{0..31} = UInt(GetRD(instruction));
  let offset : bits(21) = [GetJIMM(instruction), '0'];

  let next_pc  : bits(XLEN) = PC + 4;

  // with C extension, target is guranteed to be aligned
  let target : bits(XLEN) = PC + SignExtend(offset, XLEN);

  PC = target;

  X[rd] = next_pc;

  return Retired();
end
