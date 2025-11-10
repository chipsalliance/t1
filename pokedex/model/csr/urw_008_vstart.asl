func Read_VSTART() => CsrReadResult
begin
  return CsrReadOk(VSTART[31:0]);
end

func Write_VSTART(value: bits(32)) => Result
begin
  VSTART = value[LOG2_VLEN-1:0];

  FFI_write_CSR_hook("vstart", VSTART[31:0]);

  return Retired();
end

// utility functions

func vectorInterrupt(idx: integer)
begin
  assert(idx < VLEN);

  VSTART = idx[LOG2_VLEN-1:0];

  FFI_write_CSR_hook("vstart", idx[31:0]);
end

func clear_VSTART()
begin
  if !IsZero(VSTART) then
    // in most cases VSTART is zero
    FFI_write_CSR_hook("vstart", Zeros(XLEN));
  end

  VSTART = Zeros(LOG2_VLEN);
end

// TODO: deprecated, use clear_VSTART instead
func ClearVSTART()
begin
  clear_VSTART();
end
