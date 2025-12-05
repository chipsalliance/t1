func FFI_write_FPR_hook(fd: bits(5));

/// `ASL_read_FREG(i : bits(5))` return FLEN width floating point register value at given index.
func ASL_read_FREG(fs: bits(5)) => bits(FLEN)
begin
  return F[UInt(fs)];
end
