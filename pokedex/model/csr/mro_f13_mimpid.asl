//! ---
//! csr: "mimpid"
//! mode: "mro"
//! id: 0xF13
//! tag: "m_mode"
//! ---
//! The mimpid (Machine Implementation ID Register) is an MXLEN-bit read-only register
//! accessible exclusively in Machine Mode.
//!
//! - Value: The register is hardwired to zero, indicating that the version ID is not implemented.
//! - Exceptions: An Illegal Instruction Exception is raised under the following conditions:
//!     - Attempting to write to the register.
//!     - Attempting to read the register from a privilege level lower than Machine Mode.

func Read_MIMPID() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(CFG_MIMPID);
end

func GetRaw_MIMPID() => bits(XLEN)
begin
  return CFG_MIMPID;
end
