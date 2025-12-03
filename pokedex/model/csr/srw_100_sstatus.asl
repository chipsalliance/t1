func Read_SSTATUS() => CsrReadResult
begin
  if !IsPrivAtLeast(PRIV_MODE_S) then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(GetRaw_MSTATUS());
end

func Write_SSTATUS(value : bits(32)) => CsrWriteResult
begin
  if !IsPrivAtLeast(PRIV_MODE_S) then
    return IllegalInstruction();
  end

  SSTATUS_SIE = value[1];
  SSTATUS_SPIE = value[5];
  SSTATUS_SPP = value[8];
  MSTATUS_VS = value[10:9];
  MSTATUS_FS = value[14:13];
  SSTATUS_SUM = value[18];
  SSTATUS_MXR = value[19];

  logWrite_SSTATUS();

  return Retired();
end

func GetRaw_SSTATUS() => bits(32)
begin
  var sd : bit = '0';
  if MSTATUS_FS == '11' || MSTATUS_VS == '11' then
    sd = '1';
  end

  return [
    sd,           // [31]
    Zeros(11),    // WPRI[30:25], SDT[24], SPELP[23], WPRI[22:20],
    SSTATUS_MXR,  // [19]
    SSTATUS_SUM,  // [18]
    '0',          // WPRI
    '00',         // XS[16:15]
    MSTATUS_FS,   // [14:13]
    '00',         // WPRI[12:11]
    MSTATUS_VS,   // [10:9]
    SSTATUS_SPP,  // [8]
    '0',          // WPRI[7]
    '0',          // UBE[6] (no big endian)
    SSTATUS_SPIE, // [5]
    '000',        // WPRI[4:2]
    SSTATUS_SIE,  // [1]
    '0'           // WPRI[0]
  ];
end

func logWrite_SSTATUS()
begin
  FFI_write_CSR_hook(CSR_SSTATUS);
end
