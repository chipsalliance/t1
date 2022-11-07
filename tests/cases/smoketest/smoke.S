.global _start
_start:
    addi x1, x1, 1
    vsetivli x0, 16, e8, m1, ta, ma
    lui x30, 1
    auipc x31, 0

add_test:
    vadd.vv v2, v1, v1
    vadd.vi v1, v1, 7
    vadd.vx v1, v1, x1

chaining_test:
    vadd.vx v1, v1, x1
    vxor.vi v7, v5, 7
    vsll.vi v1, v1, 1

ld_test:
    vle8.v v4, (x30)
    j _start

