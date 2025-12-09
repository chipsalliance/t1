//! ---
//! csr: "mepc"
//! mode: "mrw"
//! id: 0x341
//! tag: "m_mode"
//! ---
//! The mepc (Machine Exception Program Counter) is an MXLEN-bit read/write register.
//! When a trap is taken into machine mode, mepc is written with the virtual address of
//! the instruction that was interrupted or that encountered the exception.
//!
//! - Behavior: The C extension is enabled, thus the lowest bit (bit 0) is always zero.
//! - Exceptions:
//!     - Illegal Instruction if accessed from a privilege level lower than Machine Mode.


func Read_MEPC() => CsrReadResult
begin
  if !IsPrivAtLeast_M() then
    return CsrReadIllegalInstruction();
  end

  return CsrReadOk(MEPC);
end

func GetRaw_MEPC() => bits(XLEN)
begin
  return MEPC;
end

func Write_MEPC(value: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  // ext C is always enabled,
  // the least bit must be masked in writing
  MEPC = [value[31:1], '0'];

  logWrite_MEPC();

  return Retired();
end

// utility functions

func logWrite_MEPC()
begin
  FFI_write_CSR_hook(CSR_MEPC);
end
