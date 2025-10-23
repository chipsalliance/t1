// explicit write mstatus.FS
set_mstatus_fs_dirty(/*log_write*/TRUE);

// fflags always read [4:0] so we don't need to mask it here
let r1 = WriteCSR(FFLAGS_IDX, value);
assert r1.is_ok;
// frm require rounding mode at last two bits
let rm : bits(32) = ZeroExtend(value[7:5], 32);
let r2 = WriteCSR(FRM_IDX, rm);
assert r2.is_ok;

return Retired();
