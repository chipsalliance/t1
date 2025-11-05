func Read_FCSR() => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  return Ok(GetRaw_FCSR());
end

func Write_FCSR(value: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  // uppper bits are ignored, required by spec
  FRM = value[7:5];
  FFLAGS = value[4:0];

  logWrite_FCSR();

  // set mstatus.fs = dirty
  makeDirty_FS();

  return Retired();
end

// utility functions

func GetRaw_FCSR() => bits(32)
begin
  return [
    Zeros(24),
    FRM,        // [7:5]
    FFLAGS      // [4:0]
  ];
end

func logWrite_FCSR()
begin
  FFI_write_CSR_hook("fcsr", GetRaw_FCSR());
end
