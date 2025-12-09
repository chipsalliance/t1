//! ---
//! csr: "vlenb"
//! mode: "uro"
//! id: 0xC22
//! tag: "vector"
//! ---
//! The vlenb (Vector Register Length in Bytes) register is an XLEN-bit read-only
//! register whose value is the length of a vector register in bytes.
//!
//! - Exceptions:
//!     - Illegal Instruction if the Vector extension is disabled (mstatus.vs == 0).
//!     - Illegal Instruction if attempting to write to the register.


func Read_VLENB() => CsrReadResult
begin
  if !isEnabled_VS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(GetRaw_VLENB());
end

func GetRaw_VLENB() => bits(XLEN)
begin
  return (VLEN DIV 8)[31:0];
end
