OUTPUT_ARCH(riscv)
ENTRY(_start)

/* The "SRAM" here should be distinguish from SOC's SRAM. The SOC SRAM is a 2560MB memory scratch pad.
 * And the "SRAM" here is for separating the address space to avoid conflict read & write.
 *
 * This memory region should be codegen from stuff like device tree.
 */
MEMORY {
  SCALAR (RWX) : ORIGIN = 0x20000000, LENGTH = 512M /* put first to set it as default */
  MMIO   (RW)  : ORIGIN = 0x00000000, LENGTH = 512M
  HEAP   (RW)  : ORIGIN = 0x40000000, LENGTH = 1024M
  SRAM   (RW)  : ORIGIN = 0x60000000, LENGTH = 1024M
}

SECTIONS {
  . = ORIGIN(SCALAR);
  .text           : { *(.text .text.*) }
  . = ALIGN(0x1000);

  .data           : { *(.data .data.*) }
  . = ALIGN(0x1000);

  .sdata          : { *(.sdata .sdata.*) }
  . = ALIGN(0x1000);

  .srodata          : { *(.srodata .srodata.*) }
  . = ALIGN(0x1000);

  .bss            : { *(.bss .bss.*) }
  _end = .; PROVIDE (end = .);

  . = ORIGIN(SRAM);
  .vdata : { *(.vdata .vdata.*) } >SRAM

  .vbss (TYPE = SHT_NOBITS) : { *(.vbss .vbss.*) } >SRAM

  __stacktop = ORIGIN(SCALAR) + LENGTH(SCALAR);  /* put stack on the top of SCALAR */
  __heapbegin = ORIGIN(HEAP);  /* put heap on the begin of DDR */
}
