//! ---
//! csr: "vtype"
//! mode: "uro"
//! id: 0xC21
//! tag: "vector"
//! ---
//! The vtype (Vector Type) register is an XLEN-bit read-only register describing
//! the default vector data type and settings.
//!
//! - Fields:
//!     - vill: Vector Illegal.
//!     - ma: Mask Agnostic.
//!     - ta: Tail Agnostic.
//!     - sew: Selected Element Width.
//!     - lmul: Vector Register Grouping Multiplier.
//! - Exceptions:
//!     - Illegal Instruction if the Vector extension is disabled (mstatus.vs == 0).
//!     - Illegal Instruction if attempting to write to the register.


func Read_VTYPE() => CsrReadResult
begin
  if !isEnabled_VS() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(VTYPE_BITS);
end

func GetRaw_VTYPE() => bits(32)
begin

  if VTYPE.ill then
      return ['1', Zeros(31)];
  end

  var sew_bits : bits(3);
  var lmul_bits : bits(3);

  case VTYPE.sew of
    when 8 => sew_bits = '000';
    when 16 => sew_bits = '001';
    when 32 => sew_bits = '010';
    when 64 => sew_bits = '011';
  end

  case VTYPE.lmul of
    when 0 => lmul_bits = '000';
    when 1 => lmul_bits = '001';
    when 2 => lmul_bits = '010';
    when 3 => lmul_bits = '011';
    when -1 => lmul_bits = '111';
    when -2 => lmul_bits = '110';
    when -3 => lmul_bits = '101';
  end

  return [
      Zeros(24),
      VTYPE.ma,   // [7]
      VTYPE.ta,   // [6]
      sew_bits,   // [5:3]
      lmul_bits   // [2:0]
  ];
end

// utility functions

func logWrite_VTYPE_VL()
begin
  FFI_write_CSR_hook(CSR_VTYPE);
  FFI_write_CSR_hook(CSR_VL);
end
