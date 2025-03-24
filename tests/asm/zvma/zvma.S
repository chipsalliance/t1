.text
.balign 16
.globl test

test:
    addi    sp, sp, -32
    sw      s0, 0(sp)
    sw      s1, 4(sp)
    sw      s2, 8(sp)
    sw      s3, 12(sp)
    sw      s4, 16(sp)
    sw      s5, 20(sp)
    sw      s6, 24(sp)
    sw      s7, 28(sp)
    j   test_1

test_1:
    # vtype=0b_11_0_00_000_000, twiden=4, alt=0, sew=8
    vsetvli s1, x0, 0x600

    # s1 shall be vl=TEW

    .insn i OP_V, 7, s2, s1, 0x841 - 0x1000 # settm s2, s1

    li      t1, 4
    .insn i OP_V, 7, s3, t1, 0x842 - 0x1000 # settk s3, t1

    # BEGIN : load into matrix

    la      a1, test_zero

    li      a0, 0
1:

    # a0 is TSS, load to mt0, row major
    # NOTE: rs1, rs2 is order swapped
    .insn r LOAD_FP, 7, 0x29, x0, a1, a0 # vlte32  a0, a1

    addi    a0, a0, 1
    blt     a0, s1, 1b

    # END : load into matrix

    vid.v v0
    vid.v v8

    # NOTE: OP_VE=0b1110111 is currently unsupported
    .insn r 0x77, 0, 0x79, x0, x0, x8 # mm.u.u  mt0, v0, v8

    # store test
    li      a0, 0
    la      a1, test_zero
    .insn r STORE_FP, 7, 0x29, x0, a1, a0 # vste32  a0, a1

    # move test
    li a1, 0xd3
    li a2, 0x100
    vsetvl x0, a2, a1
    .insn r 0x57, 6, 0x2f, x0, a0, s0 # vtmv.t.v, a0, v8
    .insn r 0x57, 6, 0x21, s0, a0, t6 # vtmv.t.v, v8, a0

wait_z:
    li a2, 0x40000
    addi    a2, a2, -1
    bnez a2, wait_z

    j test_ret

test_ret:
    li      a0, 4096
9:
    addi    a0, a0, -1
    bgtz    a0, 9b

    lw      s0, 0(sp)
    lw      s1, 4(sp)
    lw      s2, 8(sp)
    lw      s3, 12(sp)
    lw      s4, 16(sp)
    lw      s5, 20(sp)
    lw      s6, 24(sp)
    lw      s7, 28(sp)
    addi    sp, sp, 32
    ret
    


.section .vbss, "aw", @nobits
.balign 64
test_zero:
    .zero 65536
