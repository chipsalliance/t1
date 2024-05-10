#if 0
void *memcpy_rvv(void *restrict dest, void const *restrict src, size_t n) {
	unsigned char *d = dest;
	unsigned char const *s = src;
	for (size_t vl; n > 0; n -= vl, s += vl, d += vl) {
		vl = __riscv_vsetvl_e8m8(n);
		vuint8m8_t vec_src = __riscv_vle8_v_u8m8(s, vl);
		__riscv_vse8_v_u8m8(d, vec_src, vl);
	}
	return dest;
}
#endif


#ifdef MX

# a0 = dest, a1 = src, a2 = len
.global MX(memcpy_rvv_)
MX(memcpy_rvv_):
	mv a3, a0
1:
	vsetvli t0, a2, e8, MX(), ta, ma
	vle8.v v0, (a1)
	add a1, a1, t0
	sub a2, a2, t0
	vse8.v v0, (a3)
	add a3, a3, t0
	bnez a2, 1b
	ret

.global MX(memcpy_rvv_align_dest_)
MX(memcpy_rvv_align_dest_):
	mv a3, a0
#if HAS_RVV_1_0
	csrr    t0, vlenb
#else
	vsetvli t0, zero, e8, m1, ta, ma # vlenb
#endif
	bltu a2, t0, 2f # len < vlenb
	# align dest to vlenb
	sub t1, zero, a0
	addi t2, t0, -1
	and t1, t1, t2 #align = (-dest) & (vlenb-1)
	vsetvli t0, t1, e8, MX(), ta, ma
1:
	vle8.v v0, (a1)
	add a1, a1, t0
	sub a2, a2, t0
	vse8.v v0, (a3)
	add a3, a3, t0
2:
	vsetvli t0, a2, e8, MX(), ta, ma
	bnez a2, 1b
	ret

.global MX(memcpy_rvv_align_src_)
MX(memcpy_rvv_align_src_):
	mv a3, a0
#if HAS_RVV_1_0
	csrr    t0, vlenb
#else
	vsetvli t0, zero, e8, m1, ta, ma # vlen
#endif
	bltu a2, t0, 2f # len < vlen
	# align src to vlen
	sub t1, zero, a1
	addi t2, t0, -1
	and t1, t1, t2 # align = (-src) & (vlen-1)
	vsetvli t0, t1, e8, MX(), ta, ma
1:
	vle8.v v0, (a1)
	add a1, a1, t0
	sub a2, a2, t0
	vse8.v v0, (a3)
	add a3, a3, t0
2:
	vsetvli t0, a2, e8, MX(), ta, ma
	bnez a2, 1b
	ret

# combination of memcpy_rvv_align_dest and memcpy_rvv
.global MX(memcpy_rvv_align_dest_hybrid_)
MX(memcpy_rvv_align_dest_hybrid_):
	mv a3, a0
#if HAS_RVV_1_0
	csrr t0, vlenb
#else
	vsetvli t0, zero, e8, m1, ta, ma # vlen
#endif
	slli t1, t0, 8 # skip costly division for more values
	bltu a2, t1, 2f # len < vlen
	sub t1, zero, a0
	addi t2, t0, -1
	and t1, t1, t2 # align = (-dest) & (vlen-1)
	vsetvli t0, t1, e8, MX(), ta, ma # align dest to vlen
1:
	vle8.v v0, (a1)
	add a1, a1, t0
	sub a2, a2, t0
	vse8.v v0, (a3)
	add a3, a3, t0
2:
	vsetvli t0, a2, e8, MX(), ta, ma
	bnez a2, 1b
	ret


.global MX(memcpy_rvv_tail_)
MX(memcpy_rvv_tail_):
	vsetvli t0, a2, e8, MX(), ta, ma
	remu a3, a2, t0 # tail = n % vlenb
	sub a2, a2, a3 # n -= tail
	add a4, a0, a2 # end = dest + n
	mv a2, a0     # n = dest
1:
	vle8.v v8, (a1)
	add a1, a1, t0 # src += vlenb
	vse8.v v8, (a2)
	add a2, a2, t0 # dest += vlenb
	bltu a2, a4, 1b # dest < end
	# copy tail
	vsetvli zero, a3, e8, MX(), ta, ma
	vle8.v v8, (a1)
	vse8.v v8, (a2)
	ret

# this is supposed to test how well the implementation handles
# operations with an vl smaller than VLMAX
.global MX(memcpy_rvv_128_)
MX(memcpy_rvv_128_):
	li t0, 128/8
	bgt a2, t0, 1f
	mv t0, a2
1:
	vsetvli t0, t0, e8, MX(), ta, ma
	remu a3, a2, t0 # tail = n % vlenb
	sub a2, a2, a3 # n -= tail
	add a4, a0, a2 # end = dest + n
	mv a2, a0     # n = dest
1:
	vle8.v v8, (a1)
	add a1, a1, t0 # src += vlenb
	vse8.v v8, (a2)
	add a2, a2, t0 # dest += vlenb
	bltu a2, a4, 1b # dest < end
	# copy tail
	vsetvli zero, a3, e8, MX(), ta, ma
	vle8.v v8, (a1)
	vse8.v v8, (a2)
	ret

#endif

