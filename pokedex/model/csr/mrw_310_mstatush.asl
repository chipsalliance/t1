//! ---
//! csr: "mstatush"
//! mode: "mrw"
//! id: 0x310
//! tag: "m_mode"
//! ---
//! The mstatush register holds the upper 32 bits of mstatus on RV32 systems.
//!
//! - Implementation: Currently hardwired to zero.
//! - Exceptions:
//!     - Illegal Instruction if accessed from a privilege level lower than Machine Mode.


func Read_MSTATUSH() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(Zeros(32));
end

func GetRaw_MSTATUSH() => bits(XLEN)
begin
  return Zeros(32);
end

func Write_MSTATUSH(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // no-op
  return Retired();
end
