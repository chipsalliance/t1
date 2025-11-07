func Execute_JAL(instruction: bits(32)) => Result
begin
  let rd : integer{0..31} = UInt(GetRD(instruction));
  let ret_pc  : bits(XLEN) = PC + 4;
  let offset : bits(21) = [GetJIMM(instruction), '0'];

  // with C extension, target is guranteed to be aligned
  let target : bits(XLEN) = PC + SignExtend(offset, XLEN);

  PC = target;

  X[rd] = ret_pc;

  return Retired();
end
