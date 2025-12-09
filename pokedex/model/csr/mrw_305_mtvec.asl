//! ---
//! csr: "mtvec"
//! mode: "mrw"
//! id: 0x305
//! ---
//! The mtvec (Machine Trap-Vector Base-Address Register) is a read/write register
//! accessible exclusively in Machine Mode. Attempts to access this register from
//! lower privilege levels result in an Illegal Instruction Exception.
//!
//! mtvec adheres to WARL (Write Any Values, Read Legal Values) semantics. The
//! simulator implements the following behavior:
//!
//! - Supported Modes: Only Direct (00) and Vectored (01) modes are valid.
//! - Invalid Writes: If a write operation specifies an unsupported mode, the entire
//! write is ignored, and the register retains its previous value.


// Details of MTVEC arch states see states.asl

func Read_MTVEC() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(GetRaw_MTVEC());
end

func GetRaw_MTVEC() => bits(XLEN)
begin
  return [
    MTVEC_BASE,       // [31:2]
    MTVEC_MODE_BITS   // [1:0]
  ];
end

func Write_MTVEC(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // NOTE : impl defined behavior
  //
  // mtvec is WARL, we ignore the whole write for unsupported mode
  // '00' (direct) and '01' (vectored) are supported mode

  if value[1] == '0' then
    MTVEC_BASE = value[31:2];
    MTVEC_MODE_BITS = value[1:0];

    // This is the only place that modifies mtvec
    FFI_write_CSR_hook(CSR_MTVEC);
  end

  return Retired();
end
