func Execute_JALR(instruction: bits(32)) => Result
begin
  let rd : integer{0..31} = UInt(GetRD(instruction));
  let next_pc : bits(XLEN) = PC + 4;

  let imm : bits(12) = GetIMM(instruction);
  let offset : bits(XLEN) = SignExtend(imm, XLEN);

  let rs1 : integer{0..31} = UInt(GetRS1(instruction));

  // the least bit must be cleared, the spec says.
  // with C extension, target is guranteed to be aligned
  var target : bits(XLEN) = offset + X[rs1];
  target[0] = '0';

  PC = target;

  X[rd] = next_pc;

  return Retired();
end
