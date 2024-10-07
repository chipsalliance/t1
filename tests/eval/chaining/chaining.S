.text
.balign 16
.globl test

test:
    la t1, test_src_start  # t1 = addr
    la t2, test_src_end
    sub t2, t2, t1  # t2 = remaining length
loop_start:
    vsetvli t0, t2, e8, m8, ta, ma   # t0 = vl
    vle8.v v8, (t1)
    vadd.vx v16, v8, t2   # v8 from last vle
    vmul.vv v24, v16, v8  # v16 from last instr
    vsub.vv v8, v24, v16  # v16 from last 2 instr, v24 from last instr
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
