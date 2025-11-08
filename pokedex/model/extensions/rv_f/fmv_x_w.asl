func Execute_FMV_X_W(instruction: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  let rd : XRegIdx = UInt(GetRD(instruction));
  let fs1 : FRegIdx = UInt(GetRS1(instruction));

  X[rd] = F[fs1];

  // no makeDirty_FS
  PC = PC + 4;
  return Retired();
end
