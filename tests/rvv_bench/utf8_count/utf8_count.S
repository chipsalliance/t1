#if 0
size_t utf8_count_rvv(char const *buf, size_t len) {
	size_t sum = 0;
	for (size_t vl; len > 0; len -= vl, buf += vl) {
		vl = __riscv_vsetvl_e8m8(len);
		vint8m8_t v = __riscv_vle8_v_i8m8((void*)buf, vl);
		vbool1_t mask = __riscv_vmsgt_vx_i8m8_b1(v, -65, vl);
		sum += __riscv_vcpop_m_b1(mask, vl);
	}
	return sum;
}
#endif

#ifdef MX

.global MX(utf8_count_rvv_)
MX(utf8_count_rvv_):
	li a2, 0
	li a3, -65
1:
	vsetvli a4, a1, e8, MX(), ta, ma
	vle8.v v8, (a0)
	vmsgt.vx v16, v8, a3
	vcpop.m a5, v16
	add a2, a2, a5
	sub a1, a1, a4
	add a0, a0, a4
	bnez a1, 1b
	mv a0, a2
	ret

.global MX(utf8_count_rvv_align_)
MX(utf8_count_rvv_align_):
	mv a2, a0
	li a0, 0
	li a3, -65
	vsetvli t0, zero, e8, MX(), ta, ma # vlen
	bltu a1, t0, 2f # len < vlen
	# align dest to vlen
	sub t1, zero, a2
	remu t1, t1, t0 # align = (-dest) % vlen
	vsetvli t0, t1, e8, MX(), ta, ma
1:
	vle8.v v8,(a2)
	vmsgt.vx v16, v8, a3
	vcpop.m a4, v16
	add a0, a0, a4
	sub a1, a1, t0
	add a2, a2, t0
2:
	vsetvli t0, a1, e8, MX(), ta, ma
	bnez a1, 1b
	ret

.global MX(utf8_count_rvv_tail_)
MX(utf8_count_rvv_tail_):
	vsetvli t0, a1, e8, MX(), ta, ma
	remu a2, a1, t0 # tail = n % vlenb
	sub a1, a1, a2 # n -= tail
	add a3, a0, a1 # end = dest + n
	mv a1, a0     # n = dest
	li a0, 0
	li t1, -65
1:
	vle8.v v8, (a1)
	vmsgt.vx v16, v8, t1
	vcpop.m t2, v16
	add a0, a0, t2
	add a1, a1, t0 # src += vlenb
	bltu a1, a3, 1b # dest < end
	# copy tail
	vsetvli zero, a2, e8, MX(), ta, ma
	vle8.v v8, (a1)
	vmsgt.vx v16, v8, t1
	vcpop.m t2, v16
	add a0, a0, t2
	ret

# this is supposed to test how well the implementation handles
# operations with an vl smaller than VLMAX
.global MX(utf8_count_rvv_128_)
MX(utf8_count_rvv_128_):
	li t0, 128/8
	bgt a1, t0, 1f
	mv t0, a1
1:
	vsetvli t0, t0, e8, MX(), ta, ma
	remu a2, a1, t0 # tail = n % vlenb
	sub a1, a1, a2 # n -= tail
	add a3, a0, a1 # end = dest + n
	mv a1, a0     # n = dest
	li a0, 0
	li t1, -65
1:
	vle8.v v8, (a1)
	vmsgt.vx v16, v8, t1
	vcpop.m t2, v16
	add a0, a0, t2
	add a1, a1, t0 # src += vlenb
	bltu a1, a3, 1b # dest < end
	# copy tail
	vsetvli zero, a2, e8, MX(), ta, ma
	vle8.v v8, (a1)
	vmsgt.vx v16, v8, t1
	vcpop.m t2, v16
	add a0, a0, t2
	ret


.global MX(utf8_count_rvv_4x_)
MX(utf8_count_rvv_4x_):
	mv a2, a0
	li a0, 0
	li a6, -65
1:
	vsetvli a4, a1, e8, MX(), ta, ma
	vle8.v v8, (a2)
	vmsgt.vx v16, v8, a6
	vcpop.m a7, v16
	sub a1, a1, a4
	add a2, a2, a4
	vsetvli a4, a1, e8, MX(), ta, ma
	vle8.v v8, (a2)
	vmsgt.vx v16, v8, a6
	vcpop.m a3, v16
	sub a1, a1, a4
	add a2, a2, a4
	vsetvli a4, a1, e8, MX(), ta, ma
	vle8.v v8, (a2)
	vmsgt.vx v16, v8, a6
	vcpop.m a5, v16
	sub a1, a1, a4
	add a2, a2, a4
	vsetvli a4, a1, e8, MX(), ta, ma
	vle8.v v8, (a2)
	add a0, a0, a7
	add a0, a0, a3
	add a0, a0, a5
	vmsgt.vx v16, v8, a6
	vcpop.m a3, v16
	add a0, a0, a3
	sub a1, a1, a4
	add a2, a2, a4
	bnez a1, 1b
	ret

// gcc generated from unrolled intrinsics implementation:
// https://godbolt.org/z/q75c6r3Ta
.global MX(utf8_count_rvv_4x_tail_)
MX(utf8_count_rvv_4x_tail_):
	vsetvli a5, zero, e8, MX(), ta, ma
	slli t3, a5, 2
	add a1, a0, a1
	add a2, a0, t3
	mv a4, a0
	bltu a1, a2, 5f
	slli t4, a5, 1
	add t5, t4, a5
	li a0, 0
	li a6, -65
1:
	add a3, a5, a4
	vsetvli zero, zero, e8, MX(), ta, ma
	add a7, t4, a4
	vle8.v v8, (a4)
	vle8.v v16, (a3)
	vmsgt.vx v8, v8, a6
	vmsgt.vx v16, v16, a6
	vcpop.m a3, v8
	vcpop.m t1, v16
	add a3, a3, t1
	vle8.v v8, (a7)
	add a4, t5, a4
	vmsgt.vx v8, v8, a6
	vcpop.m a7, v8
	add a3, a3, a7
	vle8.v v8, (a4)
	mv a4, a2
	vmsgt.vx v8, v8, a6
	add a2, a2, t3
	vcpop.m a7, v8
	add a3, a3, a7
	add a0, a0, a3
	bgeu a1, a2, 1b
2:
	sub a3, a1, a4
	beq a1, a4, 4f
	li a2, 0
	li a1, -65
3:
	vsetvli a5, a3, e8, MX(), ta, ma
	sub a3, a3, a5
	vle8.v v8, (a4)
	add a4, a4, a5
	vmsgt.vx v8, v8, a1
	vcpop.m a5, v8
	add a2, a2, a5
	bne a3, zero, 3b
	add a0, a0, a2
4:
	ret
5:
	li a0, 0
	j 2b




#endif




