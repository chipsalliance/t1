func Read_MISA() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(GetRaw_MISA());
end

func GetRaw_MISA() => bits(XLEN)
begin
  // machine xlen is read-only 32;
  let MXL : bits(2) = '01';
  let MISA_EXTS : bits(26) = [
    // Z-N
    Zeros(13),
    // M
    '1',
    // LJKI
    '0001',
    // HGFE
    '0010',
    // DCBA
    '0101'
  ];

  return [
    MXL,
    Zeros(4),
    MISA_EXTS
  ];
end

func Write_MISA(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // NOTE : impl defined behavior
  //
  // we have no writable bits,
  // thus writing is no-op
  return Retired();
end
