func Execute_JALR(instruction: bits(32)) => Result
begin
  let rd : XRegIdx = UInt(GetRD(instruction));
  let rs1 : XRegIdx = UInt(GetRS1(instruction));
  let imm : bits(12) = GetIMM(instruction);

  let next_pc : bits(XLEN) = PC + 4;

  // the least bit must be cleared, the spec says.
  // with C extension, target is guranteed to be aligned
  var target : bits(XLEN) = X[rs1] + SignExtend(imm, XLEN);
  target[0] = '0';

  PC = target;

  X[rd] = next_pc;

  return Retired();
end
