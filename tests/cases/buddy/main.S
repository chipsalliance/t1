.globl start
start:
    li a0, 0x200 # MSTATUS_VS & (MSTATUS_VS >> 1)
    csrs mstatus, a0
    csrwi vcsr, 0

    la sp, stack_end

    call test
    csrwi 0x7cc, 0

stack_start:
    .zero 128

stack_end:
