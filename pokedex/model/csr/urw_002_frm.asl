//! ---
//! csr: "frm"
//! mode: "urw"
//! id: 0x002
//! tag: "fp"
//! ---
//! The frm (Floating-Point Rounding Mode) register is an XLEN-bit read/write register
//! that specifies the dynamic rounding mode for floating-point operations.
//!
//! - Fields: holds the lowest 3-bit value as FRM.
//! - Behavior: Writing to this register sets mstatus.fs to Dirty.
//! - Exceptions:
//!     - Illegal Instruction if the Floating-Point extension is disabled (mstatus.fs == 0).


func Read_FRM() => CsrReadResult
begin
  if !isEnabled_FS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk([
    Zeros(29),
    FRM
  ]);
end

func GetRaw_FRM() => bits(XLEN)
begin
  return [Zeros(29), FRM];
end

func Write_FRM(value: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  // uppper bits are ignored, required by spec
  FRM = value[2:0];

  logWrite_FCSR();

  // set mstatus.fs = dirty
  makeDirty_FS();

  return Retired();
end
