func Read_MIE() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(GetRaw_MIE());
end

func Write_MIE(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  MTIE = value[7];
  MEIE = value[11];

  logWrite_MIE();

  return Retired();
end

// utility functions

func GetRaw_MIE() => bits(32)
begin
  var value : bits(32) = Zeros(32);
  value[7] = MTIE;
  value[11] = MEIE;
  return value;
end

func logWrite_MIE()
begin
  FFI_write_CSR_hook(CSR_MIE);
end
