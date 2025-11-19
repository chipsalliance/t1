record FFI_ReadResult(N) {
  success : boolean;
  data    : bits(N);
};


func FFI_write_GPR_hook(rd: bits(5));
func FFI_write_FPR_hook(fd: bits(5));
func FFI_write_CSR_hook(csr: bits(12));
func FFI_write_VREG_hook(vd_mask: bits(32));

func FFI_ecall();

// fetch
func FFI_instruction_fetch_half(pc : bits(32)) => FFI_ReadResult(16);
// read
func FFI_read_physical_memory_8bits(addr : bits(32)) => FFI_ReadResult(8);
func FFI_read_physical_memory_16bits(addr : bits(32)) => FFI_ReadResult(16);
func FFI_read_physical_memory_32bits(addr : bits(32)) => FFI_ReadResult(32);
// write
func FFI_write_physical_memory_8bits(addr : bits(32), data : bits(8)) => boolean;
func FFI_write_physical_memory_16bits(addr : bits(32), data : bits(16)) => boolean;
func FFI_write_physical_memory_32bits(addr : bits(32), data : bits(32)) => boolean;

// debug
func FFI_debug_print(s: string);
func FFI_debug_unimpl_insn(name: string, data: bits(32));
func FFI_debug_issue(pc: bits(XLEN), insn: bits(32));
func FFI_debug_issue_c(pc: bits(XLEN), insn: bits(16));
func FFI_debug_unsupported_csr(csr: bits(12));

// interrupt
func FFI_machine_external_interrupt_pending() => bit;
func FFI_machine_time_interrupt_pending() => bit;

// Atomic
func FFI_load_reserved(addr : bits(32)) => FFI_ReadResult(32);
func FFI_store_conditional(addr : bits(32), data : bits(32)) => boolean;

enumeration AmoOperationType {
  AMO_SWAP,
  AMO_ADD,
  AMO_AND,
  AMO_OR,
  AMO_XOR,
  AMO_MAX,
  AMO_MIN,
  AMO_MAXU,
  AMO_MINU
};

func FFI_amo(
  operation : AmoOperationType,
  addr : bits(XLEN),
  value : bits(32)
) => FFI_ReadResult(32);

// following are exported accessors

/// `ASL_read_PC()` return the current `PC` value.
func ASL_read_PC() => bits(32)
begin
  return PC;
end

/// `ASL_read_XREG(i : bits(5))` return XLEN width GPR register value at given index.
func ASL_read_XREG(xs: bits(5)) => bits(XLEN)
begin
  return X[UInt(xs)];
end

/// `ASL_read_FREG(i : bits(5))` return FLEN width floating point register value at given index.
func ASL_read_FREG(fs: bits(5)) => bits(FLEN)
begin
  return F[UInt(fs)];
end

/// `ASL_read_VREG(vs : bits(5))` return `VLEN` width VRF value started from `vs * VLEN`.
/// Index `vs` is used as unsigned integer.
///
/// Formulated as
/// ```asl
/// let lo = UInt(vs) * VLEN;
/// let hi = lo + VLEN;
/// return VRF[hi:lo];
/// ```
func ASL_read_VREG(vs: bits(5)) => bits(VLEN)
begin
  return __VRF[UInt(vs) * VLEN +: VLEN];
end

/// `ASL_read_CSR(vs : bits(12))` return XLEN bits width CSR value at given CSR index.
/// Note that the model will stop executing with _unreachable_ error when CSR index
/// is not handled.
//
// defined in csr_dispatch.asl.j2
// func ASL_read_CSR(csr: bits(12)) => bits(XLEN)
//

//
// defined in step.asl
// func Step() => FFI_StepResult
//
