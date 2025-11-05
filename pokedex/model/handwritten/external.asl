record FFI_ReadResult(N) {
  success : boolean;
  data    : bits(N);
};

func FFI_instruction_fetch_half(pc : bits(32)) => FFI_ReadResult(16);
func FFI_emulator_do_fence();
func FFI_write_GPR_hook(reg_idx: integer{0..31}, data: bits(32));
func FFI_write_CSR_hook(name: string, value: bits(32));
func ffi_commit_insn(pc : bits(32), insn : bits(32), is_c: boolean);

func FFI_ecall();

// read
func FFI_read_physical_memory_8bits(addr : bits(32)) => FFI_ReadResult(8);
func FFI_read_physical_memory_16bits(addr : bits(32)) => FFI_ReadResult(16);
func FFI_read_physical_memory_32bits(addr : bits(32)) => FFI_ReadResult(32);
// write
func FFI_write_physical_memory_8bits(addr : bits(32), data : bits(8)) => boolean;
func FFI_write_physical_memory_16bits(addr : bits(32), data : bits(16)) => boolean;
func FFI_write_physical_memory_32bits(addr : bits(32), data : bits(32)) => boolean;

// debug
func FFI_print_str(s: string);
func FFI_print_bits_hex(v: bits(32));
func FFI_ebreak();

func ffi_debug_trap_xcpt(cause : integer, tval : bits(32));

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
  src1 : bits(32),
  src2 : bits(32),
  is_acquire : boolean,
  is_release : boolean
) => FFI_ReadResult(32);

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
func ffi_write_fpr_hook(reg_idx: freg_index, data: bits(32));
