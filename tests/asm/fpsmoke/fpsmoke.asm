.global test
test:
    # load f0 = 1.0, x1 = 1
    lui     x1,%hi(.float_number)
    flw     f0,%lo(.float_number)(x1)
    addi x1, x0, 1

    vsetivli x0, 16, e32, m1, ta, ma
    lui x30, 1
    auipc x31, 0

add_test:
    vfadd.vf v1, v1, f0
    vfadd.vv v2, v1, v0
    vfmv.f.s f1, v2

exit:
    li a0, 0x90000000
    li a1, -1
    sw a1, 4(a0)
    csrwi 0x7cc, 0

will_not_be_executed:
    vadd.vv v2, v1, v1

.float_number:
    .word 1065353216
