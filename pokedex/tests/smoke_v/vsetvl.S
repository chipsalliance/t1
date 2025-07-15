    .global test
    .type test, @function
test:
    csrr a0, vlenb
    vsetvli a1, x0, e8, m1, ta, ma
    csrr a2, vtype
    csrr a3, vl
    ret

    .size test, . - test
