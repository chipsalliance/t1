	.text
	.attribute	4, 16
	.attribute	5, "rv32i2p0_m2p0_a2p0_f2p0_d2p0_c2p0_v1p0_zve32f1p0_zve32x1p0_zve64d1p0_zve64f1p0_zve64x1p0_zvl128b1p0_zvl32b1p0_zvl64b1p0"
	.file	"test-asm.c"
	.globl	test                            # -- Begin function test
	.p2align	1
	.type	test,@function
test:                                   # @test
# %bb.0:
	addi	sp, sp, -16
	sw	s0, 12(sp)                      # 4-byte Folded Spill
	sw	s1, 8(sp)                       # 4-byte Folded Spill
	sw	s2, 4(sp)                       # 4-byte Folded Spill
	li	s2, 0
.Lpcrel_hi0:
	auipc	a1, %got_pcrel_hi(img)
	lw	a7, %pcrel_lo(.Lpcrel_hi0)(a1)
.Lpcrel_hi1:
	auipc	a1, %got_pcrel_hi(output)
	lw	t1, %pcrel_lo(.Lpcrel_hi1)(a1)
.Lpcrel_hi2:
	auipc	a1, %got_pcrel_hi(kernel)
	lw	t2, %pcrel_lo(.Lpcrel_hi2)(a1)
	li	t0, 56
	li	t3, 3
	li	a6, 16
	j	.LBB0_2
.LBB0_1:                                #   in Loop: Header=BB0_2 Depth=1
	addi	s2, s2, 1
	beq	s2, a6, .LBB0_11
.LBB0_2:                                # =>This Loop Header: Depth=1
                                        #     Child Loop BB0_4 Depth 2
                                        #       Child Loop BB0_6 Depth 3
                                        #       Child Loop BB0_8 Depth 3
                                        #       Child Loop BB0_10 Depth 3
	li	a1, 0
	slli	t6, s2, 6
	add	t6, t6, a7
	addi	t4, t6, 4
	addi	t5, t6, 8
	j	.LBB0_4
.LBB0_3:                                #   in Loop: Header=BB0_4 Depth=2
	addi	a1, a1, 1
	beq	a1, t3, .LBB0_1
.LBB0_4:                                #   Parent Loop BB0_2 Depth=1
                                        # =>  This Loop Header: Depth=2
                                        #       Child Loop BB0_6 Depth 3
                                        #       Child Loop BB0_8 Depth 3
                                        #       Child Loop BB0_10 Depth 3
	sltu	a2, s2, a1
	addi	a3, a1, 14
	sltu	a3, s2, a3
	xori	a3, a3, 1
	or	a2, a2, a3
	bnez	a2, .LBB0_3
# %bb.5:                                #   in Loop: Header=BB0_4 Depth=2
	slli	a2, a1, 1
	add	a2, a2, a1
	sub	a4, s2, a1
	slli	a2, a2, 2
	add	a2, a2, t2
	lw	a3, 0(a2)
	mul	a5, a4, t0
	add	a5, a5, t1
	li	a4, 14
	mv	s0, t6
	mv	s1, a5
.LBB0_6:                                #   Parent Loop BB0_2 Depth=1
                                        #     Parent Loop BB0_4 Depth=2
                                        # =>    This Inner Loop Header: Depth=3
	vsetvli	a0, a4, e32, m2, ta, mu
	vle32.v	v8, (s0)
	vle32.v	v10, (s1)
	vmul.vx	v8, v8, a3
	vadd.vv	v8, v10, v8
	vse32.v	v8, (s1)
	sub	a4, a4, a0
	slli	a0, a0, 2
	add	s0, s0, a0
	add	s1, s1, a0
	bnez	a4, .LBB0_6
# %bb.7:                                #   in Loop: Header=BB0_4 Depth=2
	lw	a3, 4(a2)
	li	a4, 14
	mv	s0, t4
	mv	s1, a5
.LBB0_8:                                #   Parent Loop BB0_2 Depth=1
                                        #     Parent Loop BB0_4 Depth=2
                                        # =>    This Inner Loop Header: Depth=3
	vsetvli	a0, a4, e32, m2, ta, mu
	vle32.v	v8, (s0)
	vle32.v	v10, (s1)
	vmul.vx	v8, v8, a3
	vadd.vv	v8, v10, v8
	vse32.v	v8, (s1)
	sub	a4, a4, a0
	slli	a0, a0, 2
	add	s0, s0, a0
	add	s1, s1, a0
	bnez	a4, .LBB0_8
# %bb.9:                                #   in Loop: Header=BB0_4 Depth=2
	lw	a2, 8(a2)
	li	a3, 14
	mv	a4, t5
.LBB0_10:                               #   Parent Loop BB0_2 Depth=1
                                        #     Parent Loop BB0_4 Depth=2
                                        # =>    This Inner Loop Header: Depth=3
	vsetvli	a0, a3, e32, m2, ta, mu
	vle32.v	v8, (a4)
	vle32.v	v10, (a5)
	vmul.vx	v8, v8, a2
	vadd.vv	v8, v10, v8
	vse32.v	v8, (a5)
	sub	a3, a3, a0
	slli	a0, a0, 2
	add	a4, a4, a0
	add	a5, a5, a0
	bnez	a3, .LBB0_10
	j	.LBB0_3
.LBB0_11:
	lw	s0, 12(sp)                      # 4-byte Folded Reload
	lw	s1, 8(sp)                       # 4-byte Folded Reload
	lw	s2, 4(sp)                       # 4-byte Folded Reload
	addi	sp, sp, 16
	ret
.Lfunc_end0:
	.size	test, .Lfunc_end0-test
                                        # -- End function
	.type	output,@object                  # @output
	.bss
	.globl	output
	.p2align	2, 0x0
output:
	.zero	784
	.size	output, 784

	.type	img,@object                     # @img
	.globl	img
	.p2align	2, 0x0
img:
	.zero	1024
	.size	img, 1024

	.type	kernel,@object                  # @kernel
	.globl	kernel
	.p2align	2, 0x0
kernel:
	.zero	36
	.size	kernel, 36

	.ident	"clang version 16.0.2"
	.section	".note.GNU-stack","",@progbits
	.addrsig
	.addrsig_sym output
	.addrsig_sym img
