record FFI_ReadResult(N) {
  success : boolean;
  data    : bits(N);
};

func FFI_instruction_fetch_half(pc : bits(32)) => FFI_ReadResult(16);
func FFI_emulator_do_fence();
func FFI_write_GPR_hook(reg_idx: integer{0..31}, data: bits(32));
func FFI_write_CSR_hook(idx: integer, name: string, value: bits(32));

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
