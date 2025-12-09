//! ---
//! csr: "mcause"
//! mode: "mrw"
//! id: 0x342
//! tag: "m_mode"
//! ---
//! The mcause (Machine Cause) register is an MXLEN-bit read/write register.
//!
//! - Behavior: MCAUSE is a WLRL register, it holds any value given from software.
//! - Exceptions:
//!     - Illegal Instruction if accessed from a privilege level lower than Machine Mode.


func Read_MCAUSE() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(MCAUSE);
end

func GetRaw_MCAUSE() => bits(XLEN)
begin
  return MCAUSE;
end

func Write_MCAUSE(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // NOTE : impl defined behavior
  //
  // MCAUSE is WLRL, only guaranteed to hold all supported causes
  // Here treats it as full XLEN-bit register

  MCAUSE = value;

  logWrite_MCAUSE();

  return Retired();
end

// utility functions

func logWrite_MCAUSE()
begin
  FFI_write_CSR_hook(CSR_MCAUSE);
end
