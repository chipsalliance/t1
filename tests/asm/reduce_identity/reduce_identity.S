# Reproduce: reduction accumulators must be seeded to the op identity, not 0.
#
# Every reduction below feeds an all-nonzero vs2 and a nonzero scalar vs1[0], so
# the architecturally-correct result is nonzero. The buggy RTL seeds both the
# in-lane accumulator and the cross-lane fold slots to 0; for ops whose identity
# is not 0 (min / minu / and / signed max) that 0 gets folded in via op(x, 0) and
# collapses the result. Difftest (RTL vs. spike) flags the mismatch; the results
# are also parked in callee-saved GPRs so they are visible at exit.
#
#   op          vs2 (all)   vs1[0]   correct   buggy (seed 0)
#   vredsum     5           0        80        80        <- control, 0 is identity, stays correct
#   vredminu    0x05        0x7f     0x05      0x00      <- identity should be all-ones
#   vredand     0xff        0xff     0xff      0x00      <- identity should be all-ones
#   vredmin     0x05        0x7f     0x05      0x00      <- signed, identity should be smax(0x7f)
#   vredmax     0xf0(=-16)  0xf0     0xf0      0x00      <- signed, identity should be smin(0x80)

.global test
test:
    li   a0, 16                       # vl = 16 elements
    vsetvli t0, a0, e8, m1, ta, ma

    # --- control: vredsum, identity is 0, must stay correct on both master and branch
    li   a1, 5
    vmv.v.x v8, a1                     # vs2 = {5 x16}
    vmv.s.x v16, x0                   # vs1[0] = 0
    vredsum.vs v24, v8, v16
    vmv.x.s s2, v24                    # expect 16*5 = 80 (0x50)

    # --- vredminu: unsigned min, identity is all-ones
    li   a1, 0x05
    vmv.v.x v8, a1                     # vs2 = {0x05 x16}
    li   a2, 0x7f
    vmv.s.x v16, a2                   # vs1[0] = 0x7f
    vredminu.vs v24, v8, v16
    vmv.x.s s3, v24                    # expect 0x05 ; buggy -> 0x00

    # --- vredand: bitwise and, identity is all-ones
    li   a1, 0xff
    vmv.v.x v8, a1                     # vs2 = {0xff x16}
    vmv.s.x v16, a1                   # vs1[0] = 0xff
    vredand.vs v24, v8, v16
    vmv.x.s s4, v24                    # expect 0xff ; buggy -> 0x00

    # --- vredmin: signed min, identity is smax (0x7f for e8)
    li   a1, 0x05
    vmv.v.x v8, a1                     # vs2 = {+5 x16}
    li   a2, 0x7f
    vmv.s.x v16, a2                   # vs1[0] = +127
    vredmin.vs v24, v8, v16
    vmv.x.s s5, v24                    # expect 0x05 ; buggy -> 0x00

    # --- vredmax: signed max, identity is smin (0x80 for e8)
    li   a1, 0xf0                      # -16 in two's complement e8
    vmv.v.x v8, a1                     # vs2 = {-16 x16}
    vmv.s.x v16, a1                   # vs1[0] = -16
    vredmax.vs v24, v8, v16
    vmv.x.s s6, v24                    # expect 0xf0 (-16) ; buggy -> 0x00

    ret
