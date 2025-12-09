//! ---
//! csr: "mconfigptr"
//! mode: "mro"
//! id: 0xF15
//! tag: "m_mode"
//! ---
//! The mconfigptr (Machine Configuration Pointer Register) is an MXLEN-bit read-only register
//! accessible exclusively in Machine Mode.
//!
//! - Value: The register is hardwired to zero, indicating that the configuration data structure does not exist.
//! - Exceptions: An Illegal Instruction Exception is raised under the following conditions:
//!     - Attempting to write to the register.
//!     - Attempting to read the register from a privilege level lower than Machine Mode.

func Read_MCONFIGPTR() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(CFG_MCONFIGPTR);
end

func GetRaw_MCONFIGPTR() => bits(XLEN)
begin
  return CFG_MCONFIGPTR;
end
