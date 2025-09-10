let frm = Read_FRM();
let fflags = Read_FFLAGS();

return OK([
  Zeros(24),
  frm.value[2:0],
  fflags.value[4:0]
]);
