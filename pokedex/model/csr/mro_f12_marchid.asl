//! ---
//! csr: "marchid"
//! mode: "mro"
//! id: 0xF12
//! tag: "m_mode"
//! ---
//! The marchid (Machine Architecture ID Register) is an MXLEN-bit read-only register
//! accessible exclusively in Machine Mode.
//!
//! - Value: The register is hardwired to zero, indicating that the base microarchitecture ID is not implemented.
//! - Exceptions: An Illegal Instruction Exception is raised under the following conditions:
//!     - Attempting to write to the register.
//!     - Attempting to read the register from a privilege level lower than Machine Mode.

func Read_MARCHID() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(CFG_MARCHID);
end

func GetRaw_MARCHID() => bits(XLEN)
begin
  return CFG_MARCHID;
end
