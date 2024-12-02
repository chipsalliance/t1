.text
.balign 16
.globl test

test:
    la t1, test_src_start  # t1 = addr
    la t2, test_src_end
    sub t2, t2, t1  # t2 = remaining length
    li t3, 0        # t3 = sum
loop_start:
    vsetvli t0, t2, e8, m8, ta, ma   # t0 = vl
    vle8.v v8, (t1)
    vadd.vi v8, v8, 1

    # scalar workload
    li t4, 1836311903
    li t5, 2971215073
gcd_loop:
    remu t6, t5, t4
    mv t5, t4
    mv t4, t6
    bnez t4, gcd_loop
    # end scalar workload

    vse8.v v8, (t1)

    add t1, t1, t0  # incr addr
    sub t2, t2, t0  # decr length
    bnez t2, loop_start

    ret

.section .vbss, "aw", @nobits
.balign 64
test_src_start:
    .zero 8192  # 8KB
test_src_end:
