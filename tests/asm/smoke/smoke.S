.global test
test:
    addi x1, x1, 1
    li a0, 0x400
    li a1, 0xc3
    li a2, 0x10
    vsetvl x0, a0, a1
    la x30, test_src_start
    auipc x31, 0

add_test:
    vadd.vi v0, v0, 10
    vadd.vi v16, v8, 11, v0.t
    vadd.vv v8, v16, v0
    vadd.vi v16, v8, 7, v0.t
    vadd.vx v8, v16, x1

chaining_test:
    vadd.vx v24, v16, x1
    vxor.vi v8, v24, 7
    vsll.vi v16, v8, 1

ld_test:
    vle8.v v16, (x30)

loop:
    addi a2, a2, -1
    bnez a2, add_test

    ret

will_not_be_executed:
    vadd.vv v2, v1, v1

.section .vbss, "aw", @nobits
.balign 64
test_src_start:
    .zero 8