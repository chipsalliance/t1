//! ---
//! csr: "vstart"
//! mode: "urw"
//! id: 0x008
//! tag: "vector"
//! ---
//! The vstart (Vector Start Index) register is an XLEN-bit read/write register
//! that specifies the index of the first element to be executed by a vector instruction.
//!
//! - Behavior: Writing to this register sets mstatus.fs to Dirty.
//! - Exceptions:
//!     - Illegal Instruction if the Floating-Point extension is disabled (mstatus.fs == 0).


func Read_VSTART() => CsrReadResult
begin
  if !isEnabled_VS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(ZeroExtend(VSTART, 32));
end

func GetRaw_VSTART() => bits(XLEN)
begin
  return ZeroExtend(VSTART, 32);
end

func Write_VSTART(value: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  VSTART = value[LOG2_VLEN-1:0];

  FFI_write_CSR_hook(CSR_VSTART);

  makeDirty_FS();

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
