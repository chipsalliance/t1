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

return OK(misa);
