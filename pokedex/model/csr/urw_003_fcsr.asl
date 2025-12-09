//! ---
//! csr: "fcsr"
//! mode: "urw"
//! id: 0x003
//! tag: "fp"
//! ---
//! The fcsr (Floating-Point Control and Status Register) is an XLEN-bit read/write register.
//!
//! - Fields:
//!     - frm (bits 7:5): Rounding Mode.
//!     - fflags (bits 4:0): Accrued Exceptions.
//! - Behavior: Writing to this register sets mstatus.fs to Dirty.
//! - Exceptions:
//!     - Illegal Instruction if the Floating-Point extension is disabled (mstatus.fs == 0).


func Read_FCSR() => CsrReadResult
begin
  if !isEnabled_FS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(GetRaw_FCSR());
end

func Write_FCSR(value: bits(32)) => Result
begin
  if !isEnabled_FS() then
    return IllegalInstruction();
  end

  // uppper bits are ignored, required by spec
  FRM = value[7:5];
  FFLAGS = value[4:0];

  logWrite_FCSR();

  // set mstatus.fs = dirty
  makeDirty_FS();

  return Retired();
end

// utility functions

func GetRaw_FCSR() => bits(32)
begin
  return [
    Zeros(24),
    FRM,        // [7:5]
    FFLAGS      // [4:0]
  ];
end

func logWrite_FCSR()
begin
  FFI_write_CSR_hook(CSR_FCSR);
end
