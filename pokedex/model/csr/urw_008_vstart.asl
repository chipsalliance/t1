func Read_VSTART() => CsrReadResult
begin
  return CsrReadOk(ZeroExtend(VSTART, 32));
end

func GetRaw_VSTART() => bits(XLEN)
begin
  return ZeroExtend(VSTART, 32);
end

func Write_VSTART(value: bits(32)) => Result
begin
  VSTART = value[LOG2_VLEN-1:0];

  FFI_write_CSR_hook(CSR_VSTART);

  return Retired();
end

// utility functions

func vectorInterrupt(idx: integer)
begin
  assert(idx < VLEN);

  VSTART = idx[LOG2_VLEN-1:0];

  FFI_write_CSR_hook(CSR_VSTART);
end

func clear_VSTART()
begin
  if !IsZero(VSTART) then
    // in most cases VSTART is zero
    FFI_write_CSR_hook(CSR_VSTART);
  end

  VSTART = Zeros(LOG2_VLEN);
end

// TODO: deprecated, use clear_VSTART instead
func ClearVSTART()
begin
  clear_VSTART();
end
