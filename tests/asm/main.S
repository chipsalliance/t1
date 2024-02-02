    .globl _start
_start:
    li a0, 0x2200 # MSTATUS_VS & (MSTATUS_VS >> 1)
    csrs mstatus, a0
    csrwi vcsr, 0

    la sp, stack_end

    call test
exit:
    li a0, 0x90000000
    li a1, -1
    sw a1, 4(a0)
    csrwi 0x7cc, 0

    .p2align 2
stack_start:
    .zero 128

stack_end:
