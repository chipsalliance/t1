    .globl _start
_start:
    li a0, 0x2200 # MSTATUS_VS & (MSTATUS_VS >> 1)
    csrs mstatus, a0
    csrwi vcsr, 0

    la sp, __stacktop

    // no ra to save
    call test

    // exit
    li x1, 0x10000000
    li x2, 0xdeadbeef
    sw x2, 0(x1)
    j .

    .p2align 2
