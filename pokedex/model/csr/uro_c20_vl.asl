//! ---
//! csr: "vl"
//! mode: "uro"
//! id: 0xC20
//! tag: "vector"
//! ---
//! The vl (Vector Length) register is an XLEN-bit read-only register holding the
//! number of elements to be updated by a vector instruction.
//!
//! - Exceptions:
//!     - Illegal Instruction if the Vector extension is disabled (mstatus.vs == 0)
//!     - Illegal Instruction if attempting to write to the register.

func Read_VL() => CsrReadResult
begin
  if !isEnabled_VS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(VL[31:0]);
end

func GetRaw_VL() => bits(XLEN)
begin
  return VL[31:0];
end
