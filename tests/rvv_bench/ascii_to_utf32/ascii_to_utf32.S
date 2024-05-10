#ifdef MX

#if MX_N == 2 || MX_N == 1

.global MX(ascii_to_utf32_rvv_vsseg_)
MX(ascii_to_utf32_rvv_vsseg_):
	vsetvli t0, x0, e8, MX4(), ta, ma
	vmv.v.i v0, 0
1:
	vsetvli t0, a2, e8, MX(), ta, ma
	vle8.v v0, (a1)
	vsseg4e8.v v0, (a0)
	add a1, a1, t0
	sub a2, a2, t0
	slli t0, t0, 2
	add a0, a0, t0
	bnez a2, 1b
	ret


.global MX(ascii_to_utf32_rvv_ext_)
MX(ascii_to_utf32_rvv_ext_):
1:
	vsetvli t0, a2, e8, MX(), ta, ma
	vle8.v v0, (a1)
#if HAS_RVV_1_0
	vsetvli x0, x0, e32, MX4(), ta, ma
	vzext.vf4 v8, v0
#else
	vwaddu.vx v16, v0, x0
	vsetvli x0, a2, e16, MX2(), ta, ma
	vwaddu.vx v8, v16, x0
	vsetvli x0, a2, e32, MX4(), ta, ma
#endif
	vse32.v v8, (a0)
	add a1, a1, t0
	sub a2, a2, t0
	slli t0, t0, 2
	add a0, a0, t0
	bnez a2, 1b
	ret


.global MX(ascii_to_utf32_rvv_vss_)
MX(ascii_to_utf32_rvv_vss_):
	vsetvli t0, x0, e8, MX4(), ta, ma
	vmv.v.i v0, 0
	li a3, 4
1:
	vsetvli t0, a2, e32, MX4(), ta, ma
	vse32.v v0, (a0)

	vsetvli t0, a2, e8, MX(), ta, ma
	vle8.v v8, (a1)
	vsse8.v v8, (a0), a3

	add a1, a1, t0
	sub a2, a2, t0
	slli t0, t0, 2
	add a0, a0, t0
	bnez a2, 1b
	ret

#endif
#endif

