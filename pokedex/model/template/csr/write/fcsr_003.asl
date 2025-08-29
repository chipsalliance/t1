let _ok1 = Write_FFLAGS(value);
assert _ok1.is_ok;
let _ok2 = Write_FRM(value);
assert _ok2.is_ok;

return Retired();
