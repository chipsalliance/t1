//! ---
//! csr: "vcsr"
//! mode: "urw"
//! id: 0x00F
//! tag: "vector"
//! ---
//! The vcsr (Vector Control and Status) register is an XLEN-bit read/write register.
//!
//! - Fields:
//!     - vxrm (bits 2:1): Vector Fixed-Point Rounding Mode.
//!     - vxsat (bit 0): Vector Fixed-Point Saturation Flag.
//! - Behavior: Writing to this register sets mstatus.vs to Dirty.
//! - Exceptions:
//!     - Illegal Instruction if the Vector extension is disabled (mstatus.vs == 0).


func Read_VCSR() => CsrReadResult
begin
  if !isEnabled_VS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(GetRaw_VCSR());
end

func GetRaw_VCSR() => bits(32)
begin
  return [
    Zeros(29),
    VXRM,   // [2:1]
    VXSAT   // [0]
  ];
end

func Write_VCSR(value: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  // uppper bits are ignored, required by spec
  VXRM = value[2:1];
  VXSAT = value[0];

  logWrite_VCSR();

  // set mstatus.vs = dirty
  makeDirty_VS();

  return Retired();
end

// utility functions

func logWrite_VCSR()
begin
  FFI_write_CSR_hook(CSR_VCSR);
end
