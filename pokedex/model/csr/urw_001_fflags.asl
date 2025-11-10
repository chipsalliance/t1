func Read_FFLAGS() => CsrReadResult
begin
  if !isEnabled_FS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk([
    Zeros(27),
    FFLAGS
  ]);
end

func Write_FFLAGS(value: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  // uppper bits are ignored, required by spec
  FFLAGS = value[4:0];

  logWrite_FCSR();

  // set mstatus.fs = dirty
  makeDirty_FS();

  return Retired();
end
