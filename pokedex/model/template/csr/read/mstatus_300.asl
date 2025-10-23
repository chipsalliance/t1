var mstatus_sd : bit = '0';
// TODO: add VS, XS when supported
if MSTATUS_FS == '11' || MSTATUS_VS == '11' then
  mstatus_sd = '1';
end

return OK([
  mstatus_sd,
  // WPRI[30:25], SDT[24], SPELP[23], TSR[22], TW[21], TVM[20]
  // MXR[19], SUM[18], MPRV[17], XS[16:15]
  Zeros(16),
  // FS[14:13]
  MSTATUS_FS,
  // MPP[12:11]
  MSTATUS_MPP_BITS,
  // VS[10:9], SPP
  MSTATUS_VS,
  '0',
  // MPIE
  MSTATUS_MPIE,
  // UBE, SPIE, WPRI
  '000',
  MSTATUS_MIE,
  // WPRI, SIE, WPRI
  '000'
]);
