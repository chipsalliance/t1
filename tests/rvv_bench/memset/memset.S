#if 0
void *memset(void *dst, int n, size_t len) {
	unsigned char *d = dst;
	vuint8m8_t v = __riscv_vmv_v_x_u8m8((uint8_t)n, __riscv_vsetvlmax_e8m8());
	for (size_t vl; len > 0; len -= vl, d += vl) {
		vl = __riscv_vsetvl_e8m8(len);
		__riscv_vse8_v_u8m8(d, v, vl);
	}
	return dst;
}
#endif

#ifdef MX

.global MX(memset_rvv_)
MX(memset_rvv_):
	vsetvli a3, zero, e8, MX(), ta, ma
	vmv.v.x v8, a1
	mv a1, a0
1:
	vsetvli a3, a2, e8, MX(), ta, ma
	vse8.v v8, (a1)
	sub a2, a2, a3
	add a1, a1, a3
	bnez a2, 1b
	ret


.global MX(memset_rvv_align_)
MX(memset_rvv_align_):
	vsetvli t0, zero, e8, m1, ta, ma # vlen
	vmv.v.x v8, a1
	mv a1, a0
	vsetvli t0, zero, e8, MX(), ta, ma # vlen
	bltu a2, t0, 2f # len < vlen
	# align dest to vlen
	sub t1, zero, a0
	remu t1, t1, t0 # align = (-dest) % vlen
	vsetvli t0, t1, e8, MX(), ta, ma
1:
	vse8.v v8, (a1)
	sub a2, a2, t0
	add a1, a1, t0
2:
	vsetvli t0, a2, e8, MX(), ta, ma
	bnez a2, 1b
	ret

.global MX(memset_rvv_tail_)
MX(memset_rvv_tail_):
	vsetvli t0, a2, e8, MX(), ta, ma
	vmv.v.x v8, a1
	remu a3, a2, t0 # tail = n % vlenb
	sub a2, a2, a3 # n -= tail
	add a4, a0, a2 # end = dest + n
	mv a2, a0     # n = dest
1:
	vse8.v v8, (a2)
	add a2, a2, t0 # dest += vlenb
	bltu a2, a4, 1b # dest < end
	# handle tail
	vsetvli zero, a3, e8, MX(), ta, ma
	vse8.v v8, (a2)
	ret

.global MX(memset_rvv_tail_4x_)
MX(memset_rvv_tail_4x_):
	vsetvli t0, a2, e8, MX(), ta, ma
	vmv.v.x v8, a1
	slli t1, t0, 2
	mv a5, a0
	mv a3, a2
	bltu a2, t1, 2f
	remu a3, a2, t1 # tail = n % (vlenb*4)
	sub a2, a2, a3 # n -= tail
	add a4, a0, a2 # end = dest + n
1:
	vse8.v v8, (a5)
	add a5, a5, t0 # dest += vlenb
	vse8.v v8, (a5)
	add a5, a5, t0 # dest += vlenb
	vse8.v v8, (a5)
	add a5, a5, t0 # dest += vlenb
	vse8.v v8, (a5)
	add a5, a5, t0 # dest += vlenb
	bltu a5, a4, 1b # dest < end
	# handle tail
2:
	vsetvli a4, a3, e8, MX(), ta, ma
	vse8.v v8, (a5)
	sub a3, a3, a4
	add a5, a5, a4
	bnez a3, 2b
	ret

#endif
