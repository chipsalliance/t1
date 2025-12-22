OUTPUT_ARCH(riscv)
ENTRY(_start)

MEMORY {
  SRAM   (RWX) : ORIGIN = 0x80000000, LENGTH = 512M /* put first to set it as default */
  MMIO   (RW)  : ORIGIN = 0x40000000, LENGTH = 4K
}

SECTIONS {
  . = ORIGIN(SRAM);
  .text           : {
    /* Explicitly put out `_start` at the beginning of the SRAM */
    *(.text.init)

    *(.text .text.*)
  }
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

  __heapbegin = .;

  __stacktop = ORIGIN(SRAM) + LENGTH(SRAM);

  . = ALIGN(0x1000);

  . = ORIGIN(MMIO) + 0x4;
  pokedex_mmio_exit = .;

  . = ORIGIN(MMIO) + LENGTH(MMIO) - 0x8;
  tohost = .;

  . = ORIGIN(MMIO) + LENGTH(MMIO) - 0x4;
  fromhost = .;
}
