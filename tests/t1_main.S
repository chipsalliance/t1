    .globl _start
_start:
    li a0, 0x2200 # MSTATUS_VS & (MSTATUS_VS >> 1)
    csrs mstatus, a0
    csrwi vcsr, 0

    la sp, __stacktop

    // no ra to save
    call test

    // exit
    csrwi 0x7cc, 0

    .p2align 2
