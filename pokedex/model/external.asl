// hook
func FFI_instruction_fetch(pc : bits(32)) => bits(32);
func FFI_emulator_do_fence();
func FFI_write_GPR_hook(reg_idx: integer{0..31}, data: bits(32));

func FFI_ecall();
func FFI_ebreak();

// read
func FFI_read_physical_memory_8bits(addr : bits(32)) => bits(8);
func FFI_read_physical_memory_16bits(addr : bits(32)) => bits(16);
func FFI_read_physical_memory_32bits(addr : bits(32)) => bits(32);
// write
func FFI_write_physical_memory_8bits(addr : bits(32), data : bits(8));
func FFI_write_physical_memory_16bits(addr : bits(32), data : bits(16));
func FFI_write_physical_memory_32bits(addr : bits(32), data : bits(32));

// debug
func FFI_print_str(s: string);
func FFI_print_bits_hex(v: bits(32));
