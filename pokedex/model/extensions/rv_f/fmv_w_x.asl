func Execute_FMV_W_X(instruction: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  let fd : FRegIdx = UInt(GetRD(instruction));
  let rs1 : XRegIdx = UInt(GetRS1(instruction));

  F[fd] = X[rs1];

  makeDirty_FS();
  PC = PC + 4;
  return Retired();
end
