//! ---
//! csr: "mvendorid"
//! mode: "mro"
//! id: 0xF11
//! tag: "m_mode"
//! ---
//! The mvendorid (Machine Vendor ID Register) is an MXLEN-bit read-only register
//! accessible exclusively in Machine Mode.
//!
//! - Value: The register is hardwired to zero, indicating that the JEDEC manufacturer ID is not implemented.
//! - Exceptions: An Illegal Instruction Exception is raised under the following conditions:
//!     - Attempting to write to the register.
//!     - Attempting to read the register from a privilege level lower than Machine Mode.

func Read_MVENDORID() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(CFG_MVENDORID);
end

func GetRaw_MVENDORID() => bits(XLEN)
begin
  return CFG_MVENDORID;
end
