//! ---
//! csr: "vxrm"
//! mode: "urw"
//! id: 0x00A
//! tag: "vector"
//! ---
//! The vxrm (Vector Fixed-Point Rounding Mode) register is an XLEN-bit read/write register
//! that holds the lowest 2-bit value as rounding mode for fixed-point instructions.
//!
//! - Behavior: Writing to this register sets mstatus.vs to Dirty.
//! - Exceptions:
//!     - Illegal Instruction if the Vector extension is disabled (mstatus.vs == 0).


func Read_VXRM() => CsrReadResult
begin
  if !isEnabled_VS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk([Zeros(30), VXRM]);
end

func GetRaw_VXRM() => bits(XLEN)
begin
  return [Zeros(30), VXRM];
end

func Write_VXRM(value: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  // uppper bits are ignored, required by spec
  VXRM = value[1:0];

  logWrite_VCSR();

  // set mstatus.vs = dirty
  makeDirty_VS();

  return Retired();
end
