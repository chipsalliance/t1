func Read_MISA() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

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

  let misa : bits(32) = [
    MXL,
    Zeros(4),
    MISA_EXTS
  ];

  return CsrReadOk(misa);
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
