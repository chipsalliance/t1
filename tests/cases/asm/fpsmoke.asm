.global test
test:
    addi x1, x1, 1
    vsetivli x0, 16, e32, m1, ta, ma
    lui x30, 1
    auipc x31, 0

add_test:
    vfadd.vf v2, v1, v0


exit:
    # Write msimend to exit simulation.
    csrwi 0x7cc, 0

will_not_be_executed:
    vadd.vv v2, v1, v1
