    .globl _start
_start:
    li a0, 0x2200 # MSTATUS_VS & (MSTATUS_VS >> 1)
    csrs mstatus, a0
    csrwi vcsr, 0

    li sp, 0xffff0000  # 4GB - 64KB

    call test
    csrwi 0x7cc, 0

    .p2align 2
stack_start:
    .zero 128

stack_end:
