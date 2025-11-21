func Execute_FSQRT_S(instruction: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  let fd: FRegIdx = UInt(GetRD(instruction));
  let fs1: FRegIdx = UInt(GetRS1(instruction));

  let (rm: RM, valid: boolean) = resolveFrmDynamic(GetRM(instruction));
  if !valid then
    return IllegalInstruction();
  end

  var src1: bits(32) = F[fs1];

  var res: F32_Flags = riscv_f32_sqrt(rm, src1);

  F[fd] = res.value;
  accureFFlags(res.fflags);

  makeDirty_FS();
  PC = PC + 4;
  return Retired();
end
