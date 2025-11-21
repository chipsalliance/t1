func Execute_FCLASS_S(instruction: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  let rd : XRegIdx = UInt(GetRD(instruction));
  let fs1 : FRegIdx = UInt(GetRS1(instruction));

  var mask : bits(10) = riscv_fclass_f32(F[fs1]);

  X[rd] = ZeroExtend(mask, XLEN);

  // no makeDirty_FS
  PC = PC + 4;
  return Retired();
end
