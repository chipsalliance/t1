let fflags : bits(12) = ZeroExtend('001', 12);
let frm : bits(12) = ZeroExtend('010', 12);

var mr = ReadCSR(MSTATUS_IDX);
assert mr.is_ok;
mr.value[MSTATUS_FS_HI:MSTATUS_FS_LO] = '11'; // set dirty
let mw = WriteCSR(MSTATUS_IDX, mr.value);
assert mw.is_ok;

// fflags always read [4:0] so we don't need to mask it here
let r1 = WriteCSR(fflags, value);
assert r1.is_ok;
// frm require rounding mode at last two bits
let rm : bits(32) = ZeroExtend(value[7:5], 32);
let r2 = WriteCSR(frm, rm);
assert r2.is_ok;

return Retired();
