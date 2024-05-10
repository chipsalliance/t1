/*
 * TODO: This currently only works for VLEN<=256.
 *       I think rvv 1.0 should only vrgatherei16.vv here in the future.
 */

#ifdef MX


# a0 = ptr, a1 = len
.global MX(byteswap32_rvv_gather_)
MX(byteswap32_rvv_gather_):
	vsetvli t0, x0, e8, MX(), ta, ma
	vid.v v0
	vand.vi v8, v0, 3
	vrsub.vi v8, v8, 3
	vsrl.vi v0, v0, 2
	vsll.vi v0, v0, 2
	vadd.vv v0, v0, v8 # i/8*8 + (7-1%8)
1:
	vsetvli t0, a1, e32, MX(), ta, ma
	vle32.v v8, (a0)
	slli t1, t0, 2
	vsetvli x0, t1, e8, MX(), ta, ma
	vrgather.vv v16, v8, v0
	vsetvli x0, t0, e32, MX(), ta, ma
	vse32.v v16, (a0)
	sub a1, a1, t0
	add a0, a0, t1
	bnez a1, 1b
	ret
#endif

#if MX_N == 2

.macro byteswap32_rvv_m1_gathers n
	.global byteswap32_rvv_m1_gathers_m\n
	byteswap32_rvv_m1_gathers_m\n:
		vsetvli t0, x0, e8, m1, ta, ma
		vid.v v0
		vand.vi v8, v0, 3
		vrsub.vi v8, v8, 3
		vsrl.vi v0, v0, 2
		vsll.vi v0, v0, 2
		vadd.vv v0, v0, v8 # i/8*8 + (7-1%8)
	1:
		vsetvli t0, a1, e32, m\n, ta, ma
		vle32.v v8, (a0)
		vsetvli t1, x0, e8, m1, ta, ma
		vrgather.vv v16, v8, v0
	.ifge \n-2
		vrgather.vv v17, v9, v0
	.ifge \n-4
		vrgather.vv v18, v10, v0
		vrgather.vv v19, v11, v0
	.ifge \n-8
		vrgather.vv v20, v12, v0
		vrgather.vv v21, v13, v0
		vrgather.vv v22, v14, v0
		vrgather.vv v23, v15, v0
	.endif
	.endif
	.endif
		vsetvli x0, t0, e32, m\n, ta, ma
		vse32.v v16, (a0)
		sub a1, a1, t0
		slli t0, t0, 2
		add a0, a0, t0
		bnez a1, 1b
		ret
.endm

byteswap32_rvv_m1_gathers 2
#endif
#if MX_N == 4
byteswap32_rvv_m1_gathers 4
#endif
#if MX_N == 8
byteswap32_rvv_m1_gathers 8
#endif


