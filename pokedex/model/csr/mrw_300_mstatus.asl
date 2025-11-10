func Read_MSTATUS() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(GetRaw_MSTATUS());
end

func Write_MSTATUS(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end
 
  // NOTE : impl defined behavior
  //
  // we just keep the previous privilege mode if value invalid
  let new_mpp = value[12:11];
  if is_valid_privilege(new_mpp) then
    MSTATUS_MPP_BITS = new_mpp;
  end

  MSTATUS_VS = value[10:9];
  MSTATUS_FS = value[14:13];
  MSTATUS_MPIE = value[7];
  MSTATUS_MIE = value[3];

  logWrite_MSTATUS();

  return Retired();
end

// utility functions

func GetRaw_MSTATUS() => bits(32)
begin
  var sd : bit = '0';
  if MSTATUS_FS == '11' || MSTATUS_VS == '11' then
    sd = '1';
  end

  return [
    sd,           // [31]

    // WPRI[30:25], SDT[24], SPELP[23], TSR[22], TW[21], TVM[20]
    // MXR[19], SUM[18], MPRV[17], XS[16:15]
    Zeros(16),
    
    MSTATUS_FS,         // [14:13]
    MSTATUS_MPP_BITS,   // [12:11]
    MSTATUS_VS,         // [10:9]
    '0',                // [8] SPP
    MSTATUS_MPIE,       // [7]

    // UBE, SPIE, WPRI
    '000',

    MSTATUS_MIE,        // [3]
    // WPRI, SIE, WPRI
    '000'
  ];
end

func logWrite_MSTATUS()
begin
  FFI_write_CSR_hook("mstatus", GetRaw_MSTATUS());
end

func isEnabled_FS() => boolean
begin
  return MSTATUS_FS != '00';
end

func isEnabled_VS() => boolean
begin
  return MSTATUS_VS != '00';
end

func makeDirty_FS() begin
  MSTATUS_FS = '11';

  // TODO : track fine-grained
  logWrite_MSTATUS();
end

func makeDirty_VS() begin
  MSTATUS_VS = '11';

  // TODO : track fine-grained
  logWrite_MSTATUS();
end
