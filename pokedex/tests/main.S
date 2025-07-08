    .globl _start

// tohost address define in ld
do_tohost:
    lui     a5,%hi(tohost)
    sw      a0,%lo(tohost)(a5)
    ret

trap_handler:
    li a0, 1
    call do_tohost
    ebreak

_start:
    csrr a0, mhartid
    li x1, 0
    li x2, 0
    li x3, 0
    li x4, 0
    li x5, 0
    li x6, 0
    li x7, 0
    li x8, 0
    li x9, 0
    li x10, 0
    li x11, 0
    li x12, 0
    li x13, 0
    li x14, 0
    li x15, 0
    li x16, 0
    li x17, 0
    li x18, 0
    li x19, 0
    li x20, 0
    li x21, 0
    li x22, 0
    li x23, 0
    li x24, 0
    li x25, 0
    li x26, 0
    li x27, 0
    li x28, 0
    li x29, 0
    li x30, 0
    li x31, 0

    la sp, __stacktop

    la t0, trap_handler
    csrw mtvec, t0

    // no ra to save
    call test

    // exit
    li a0, 1
    call do_tohost
    ebreak

    .p2align 2

