//! ---
//! csr: "mhartid"
//! mode: "mro"
//! id: 0xF14
//! tag: "m_mode"
//! ---
//! The mhartid (Machine Hart ID Register) is an MXLEN-bit read-only register
//! accessible exclusively in Machine Mode.
//!
//! - Value: The register is hardwired to zero, indicating that there is only one hardware thread.
//! - Exceptions: An Illegal Instruction Exception is raised under the following conditions:
//!     - Attempting to write to the register.
//!     - Attempting to read the register from a privilege level lower than Machine Mode.

func Read_MHARTID() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(CFG_MHARTID);
end

func GetRaw_MHARTID() => bits(XLEN)
begin
  return CFG_MHARTID;
end
