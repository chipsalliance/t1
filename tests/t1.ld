OUTPUT_ARCH(riscv)
ENTRY(_start)

MEMORY {
  /* TODO We need a separate region to separate execute and read write permission */
  SRAM   (RWX) : ORIGIN = 0x20000000, LENGTH = 512M /* put first to set it as default */
  MMIO   (RW)  : ORIGIN = 0x10000000, LENGTH = 512M
  DRAM   (RW)  : ORIGIN = 0x40000000, LENGTH = 1024M
}

SECTIONS {
  . = ORIGIN(SRAM);
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
  . = ALIGN(0x1000);

  .vdata : { *(.vdata .vdata.*) } >SRAM

  .vbss (TYPE = SHT_NOBITS) : { *(.vbss .vbss.*) } >SRAM
  . = ALIGN(0x1000);

  __heapbegin = .; /* put heap at the top of SRAM */

  __drambegin = ORIGIN(DRAM); /* provide this symbol for emulator runtime to create dram malloc */
  __stacktop = ORIGIN(DRAM) + LENGTH(DRAM);  /* put stack on the top of DRAM */
}
