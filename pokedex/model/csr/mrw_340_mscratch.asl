//! ---
//! csr: "mscratch"
//! mode: "mrw"
//! id: 0x340
//! tag: "m_mode"
//! ---
//! The mscratch register is an MXLEN-bit read/write register dedicated for use by
//! machine mode trap handlers.
//!
//! - Exceptions:
//!     - Illegal Instruction if accessed from a privilege level lower than Machine Mode.


func Read_MSCRATCH() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(MSCRATCH);
end

func GetRaw_MSCRATCH() => bits(XLEN)
begin
  return MSCRATCH;
end

func Write_MSCRATCH(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  MSCRATCH = value;

  // This is the only place that modifies mscratch
  FFI_write_CSR_hook(CSR_MSCRATCH);

  return Retired();
end
