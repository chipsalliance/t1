func Read_FRM() => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  return Ok([
    Zeros(29),
    FRM
  ]);
end

func Write_FRM(value: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  // uppper bits are ignored, required by spec
  FRM = value[2:0];

  logWrite_FCSR();

  // set mstatus.fs = dirty
  makeDirty_FS();

  return Retired();
end