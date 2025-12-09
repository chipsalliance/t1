//! ---
//! csr: "mip"
//! mode: "mrw"
//! id: 0x344
//! tag: "m_mode"
//! ---
//! The mip (Machine Interrupt Pending) register is an MXLEN-bit read/write register
//! containing information on pending interrupts.
//!
//! - Implemented Fields:
//!     - MEIP (bit 11): Machine External Interrupt Pending.
//!     - MTIP (bit 7): Machine Timer Interrupt Pending.
//! - Behavior: Write operations are currently no-ops as these bits are driven by external signals.
//! - Exceptions:
//!     - Illegal Instruction if accessed from a privilege level lower than Machine Mode.

func Read_MIP() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  var value : bits(32) = Zeros(32);
  value[7] = getExternal_MTIP;
  value[11] = getExternal_MEIP;
  return CsrReadOk(value);
end

func GetRaw_MIP() => bits(XLEN)
begin
  // TODO: the exact semantics should be discussed later
  return Zeros(32);
end

func Write_MIP(value : bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // currently we have no writable bits,
  // thus it is no-op
  return Retired();
end
