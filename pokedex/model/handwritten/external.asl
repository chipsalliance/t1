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

// func logWrite_X(rd: XRegIdx)
// begin
//   FFI_write_GPR_hook(rd as bits(5));
// end

// func logWrite_F(fd: FRegIdx)
// begin
//   FFI_write_FPR_hook(fd as bits(5));
// end

func ffi_yield_softfloat_exception_flags() => integer;
func ffi_f32_add(rs1 : bits(32), rs2 : bits(32), rm: bits(3)) => bits(32);
func ffi_f32_sub(rs1 : bits(32), rs2 : bits(32), rm: bits(3)) => bits(32);
func ffi_f32_mul(rs1 : bits(32), rs2 : bits(32), rm: bits(3)) => bits(32);
func ffi_f32_div(rs1 : bits(32), rs2 : bits(32), rm: bits(3)) => bits(32);
func ffi_f32_sqrt(src : bits(32), rm: bits(3)) => bits(32);
func ffi_f32_lt(s1 : bits(32), s2 : bits(32)) => boolean;
func ffi_f32_le(s1 : bits(32), s2 : bits(32)) => boolean;
func ffi_f32_lt_quiet(s1 : bits(32), s2 : bits(32)) => boolean;
func ffi_f32_eq(s1 : bits(32), s2 : bits(32)) => boolean;
func ffi_f32_mulAdd(s1 : bits(32), s2 : bits(32), s3 : bits(32), rm : bits(3)) => bits(32);
func ffi_f32_to_i32(src : bits(32), rm : bits(3)) => integer;
func ffi_f32_to_ui32(src : bits(32), rm : bits(3)) => integer;
func ffi_i32_to_f32(src : bits(32), rm : bits(3)) => bits(32);
func ffi_ui32_to_f32(src : bits(32), rm : bits(3)) => bits(32);
func ffi_f32_isSignalingNaN(src : bits(32)) => boolean;

// following are exported accessors

func ASL_read_PC() => bits(32)
begin
  return PC;
end

func ASL_read_XREG(xs: bits(5)) => bits(XLEN)
begin
  return X[UInt(xs)];
end

func ASL_read_FREG(fs: bits(5)) => bits(FLEN)
begin
  return F[UInt(fs)];
end

func ASL_read_VREG(vs: bits(5)) => bits(VLEN)
begin
  return __VRF[UInt(vs) * VLEN +: VLEN];
end

// defined in csr_dispatch.asl.j2
// func ASL_read_CSR(csr: bits(12)) => bits(XLEN)
