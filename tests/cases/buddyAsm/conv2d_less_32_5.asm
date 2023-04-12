	.text
	.attribute	4, 16
	.attribute	5, "rv32i2p0_m2p0_a2p0_f2p0_d2p0_c2p0_v1p0_zve32f1p0_zve32x1p0_zve64d1p0_zve64f1p0_zve64x1p0_zvl128b1p0_zvl32b1p0_zvl64b1p0"
	.file	"test-asm.c"
	.globl	test                            # -- Begin function test
	.p2align	1
	.type	test,@function
test:                                   # @test
# %bb.0:
	addi	sp, sp, -32
	sw	s0, 28(sp)                      # 4-byte Folded Spill
	sw	s1, 24(sp)                      # 4-byte Folded Spill
	sw	s2, 20(sp)                      # 4-byte Folded Spill
	sw	s3, 16(sp)                      # 4-byte Folded Spill
	sw	s4, 12(sp)                      # 4-byte Folded Spill
	li	s4, 0
.Lpcrel_hi0:
	auipc	a1, %got_pcrel_hi(img)
	lw	a7, %pcrel_lo(.Lpcrel_hi0)(a1)
.Lpcrel_hi1:
	auipc	a1, %got_pcrel_hi(output)
	lw	t1, %pcrel_lo(.Lpcrel_hi1)(a1)
.Lpcrel_hi2:
	auipc	a1, %got_pcrel_hi(kernel)
	lw	t2, %pcrel_lo(.Lpcrel_hi2)(a1)
	li	t0, 112
	li	t3, 5
	li	a6, 32
	j	.LBB0_2
.LBB0_1:                                #   in Loop: Header=BB0_2 Depth=1
	addi	s4, s4, 1
	beq	s4, a6, .LBB0_15
.LBB0_2:                                # =>This Loop Header: Depth=1
                                        #     Child Loop BB0_4 Depth 2
                                        #       Child Loop BB0_6 Depth 3
                                        #       Child Loop BB0_8 Depth 3
                                        #       Child Loop BB0_10 Depth 3
                                        #       Child Loop BB0_12 Depth 3
                                        #       Child Loop BB0_14 Depth 3
	li	a1, 0
	slli	s3, s4, 7
	add	s3, s3, a7
	addi	t4, s3, 4
	addi	t5, s3, 8
	addi	t6, s3, 12
	addi	s2, s3, 16
	j	.LBB0_4
.LBB0_3:                                #   in Loop: Header=BB0_4 Depth=2
	addi	a1, a1, 1
	beq	a1, t3, .LBB0_1
.LBB0_4:                                #   Parent Loop BB0_2 Depth=1
                                        # =>  This Loop Header: Depth=2
                                        #       Child Loop BB0_6 Depth 3
                                        #       Child Loop BB0_8 Depth 3
                                        #       Child Loop BB0_10 Depth 3
                                        #       Child Loop BB0_12 Depth 3
                                        #       Child Loop BB0_14 Depth 3
	sltu	a2, s4, a1
	addi	a3, a1, 28
	sltu	a3, s4, a3
	xori	a3, a3, 1
	or	a2, a2, a3
	bnez	a2, .LBB0_3
# %bb.5:                                #   in Loop: Header=BB0_4 Depth=2
	slli	a2, a1, 2
	add	a2, a2, a1
	sub	a3, s4, a1
	slli	a2, a2, 2
	add	a5, t2, a2
	lw	s0, 0(a5)
	mul	a2, a3, t0
	add	a2, a2, t1
	li	s1, 28
	mv	a4, s3
	mv	a3, a2
.LBB0_6:                                #   Parent Loop BB0_2 Depth=1
                                        #     Parent Loop BB0_4 Depth=2
                                        # =>    This Inner Loop Header: Depth=3
	vsetvli	a0, s1, e32, m2, ta, mu
	vle32.v	v8, (a4)
	vle32.v	v10, (a3)
	vmul.vx	v8, v8, s0
	vadd.vv	v8, v10, v8
	vse32.v	v8, (a3)
	sub	s1, s1, a0
	slli	a0, a0, 2
	add	a4, a4, a0
	add	a3, a3, a0
	bnez	s1, .LBB0_6
# %bb.7:                                #   in Loop: Header=BB0_4 Depth=2
	lw	s0, 4(a5)
	li	s1, 28
	mv	a4, t4
	mv	a3, a2
.LBB0_8:                                #   Parent Loop BB0_2 Depth=1
                                        #     Parent Loop BB0_4 Depth=2
                                        # =>    This Inner Loop Header: Depth=3
	vsetvli	a0, s1, e32, m2, ta, mu
	vle32.v	v8, (a4)
	vle32.v	v10, (a3)
	vmul.vx	v8, v8, s0
	vadd.vv	v8, v10, v8
	vse32.v	v8, (a3)
	sub	s1, s1, a0
	slli	a0, a0, 2
	add	a4, a4, a0
	add	a3, a3, a0
	bnez	s1, .LBB0_8
# %bb.9:                                #   in Loop: Header=BB0_4 Depth=2
	lw	s0, 8(a5)
	li	s1, 28
	mv	a4, t5
	mv	a3, a2
.LBB0_10:                               #   Parent Loop BB0_2 Depth=1
                                        #     Parent Loop BB0_4 Depth=2
                                        # =>    This Inner Loop Header: Depth=3
	vsetvli	a0, s1, e32, m2, ta, mu
	vle32.v	v8, (a4)
	vle32.v	v10, (a3)
	vmul.vx	v8, v8, s0
	vadd.vv	v8, v10, v8
	vse32.v	v8, (a3)
	sub	s1, s1, a0
	slli	a0, a0, 2
	add	a4, a4, a0
	add	a3, a3, a0
	bnez	s1, .LBB0_10
# %bb.11:                               #   in Loop: Header=BB0_4 Depth=2
	lw	s0, 12(a5)
	li	s1, 28
	mv	a4, t6
	mv	a3, a2
.LBB0_12:                               #   Parent Loop BB0_2 Depth=1
                                        #     Parent Loop BB0_4 Depth=2
                                        # =>    This Inner Loop Header: Depth=3
	vsetvli	a0, s1, e32, m2, ta, mu
	vle32.v	v8, (a4)
	vle32.v	v10, (a3)
	vmul.vx	v8, v8, s0
	vadd.vv	v8, v10, v8
	vse32.v	v8, (a3)
	sub	s1, s1, a0
	slli	a0, a0, 2
	add	a4, a4, a0
	add	a3, a3, a0
	bnez	s1, .LBB0_12
# %bb.13:                               #   in Loop: Header=BB0_4 Depth=2
	lw	a5, 16(a5)
	li	s0, 28
	mv	a4, s2
.LBB0_14:                               #   Parent Loop BB0_2 Depth=1
                                        #     Parent Loop BB0_4 Depth=2
                                        # =>    This Inner Loop Header: Depth=3
	vsetvli	a0, s0, e32, m2, ta, mu
	vle32.v	v8, (a4)
	vle32.v	v10, (a2)
	vmul.vx	v8, v8, a5
	vadd.vv	v8, v10, v8
	vse32.v	v8, (a2)
	sub	s0, s0, a0
	slli	a0, a0, 2
	add	a4, a4, a0
	add	a2, a2, a0
	bnez	s0, .LBB0_14
	j	.LBB0_3
.LBB0_15:
	lw	s0, 28(sp)                      # 4-byte Folded Reload
	lw	s1, 24(sp)                      # 4-byte Folded Reload
	lw	s2, 20(sp)                      # 4-byte Folded Reload
	lw	s3, 16(sp)                      # 4-byte Folded Reload
	lw	s4, 12(sp)                      # 4-byte Folded Reload
	addi	sp, sp, 32
	ret
.Lfunc_end0:
	.size	test, .Lfunc_end0-test
                                        # -- End function
	.type	output,@object                  # @output
	.bss
	.globl	output
	.p2align	2, 0x0
output:
	.zero	3136
	.size	output, 3136

	.type	img,@object                     # @img
	.globl	img
	.p2align	2, 0x0
img:
	.zero	4096
	.size	img, 4096

	.type	kernel,@object                  # @kernel
	.globl	kernel
	.p2align	2, 0x0
kernel:
	.zero	100
	.size	kernel, 100

	.ident	"clang version 16.0.2"
	.section	".note.GNU-stack","",@progbits
	.addrsig
	.addrsig_sym output
	.addrsig_sym img
