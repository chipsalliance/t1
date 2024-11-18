OUTPUT_ARCH(riscv)
ENTRY(_start)

MEMORY {
  SCALAR (RWX) : ORIGIN = 0x20000000, LENGTH = 512M /* put first to set it as default */
  MMIO   (RW)  : ORIGIN = 0x00000000, LENGTH = 512M
  DDR    (RW)  : ORIGIN = 0x40000000, LENGTH = 2048M
  SRAM   (RW)  : ORIGIN = 0xc0000000, LENGTH = 1024M /* TODO: read from config */
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
  __heapbegin = ORIGIN(DDR);  /* put heap on the begin of DDR */
}
