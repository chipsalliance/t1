    .globl _start
    .globl heap_start
_start:
    li a0, 0x2200 # MSTATUS_VS & (MSTATUS_VS >> 1)
    csrs mstatus, a0
    csrwi vcsr, 0

    la sp, stack_end

    call test
    csrwi 0x7cc, 0

    .p2align 2

heap_start:
    .zero 1073741824

heap_end:
stack_start:
    .zero 0x10000

stack_end:
