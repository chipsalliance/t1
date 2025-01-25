.text
.balign 16
.globl mmm
.type mmm,@function
# assume VLEN >= 4096, BN = 256, SEW = 16 * 2 = 32
# we only support LMUL = 1 for now
# P, A, B, AB should have 256 elements
mmm:
    # quite SIMD
    li  t0, 128 # in case way > 31
    vsetvli zero, t0, e32, m1, ta, ma
    # stride
    li  t1, 8
    # start loop of niter + 1 times
    li  t4,0
1:
    # AB = B_i*A + AB
    # !!!!!! important: lw here assumes SEW = 32
    # T0 is used in vmacc, do not use for temp now!
    lw  t0, 0(a2)
    addi a2, a2, 4 # advance B by a SEW

    # carry for ABV_0
    vmv.v.i v30,0 
    # loop variable
    li  t5,0

    # ---
    # macc (V=a1, VV=v10, VVN=10, ngroupreg=2)
    # ---

    # load one group of values from arg
    # offset of one group
    # !!! important: assume nreg = 8 and sew = 32
    # log(8) + log(32/8) = 5
    slli t2,t5,5
    add  t3,t2,a1
    vlsseg2e32.v v10, (t3), t1
    add  t3,t2,a0
    vlsseg2e32.v v20, (t3), t1
        vmacc.vx v20, t0, v10
        vmacc.vx v21, t0, v11
    # store one group of AB
    vssseg2e32.v v20, (t3), t1

        # ---
        # propagate_niter
        # ---

        # start loop of niter + 1 times
        # use T2 as outer loop index
        li  t2,0
    9:
        # mask
        # set TV2 for every propagate()
        # set TV2 every time (see slide1up below)
        li  t0,65535
        vmv.v.x v31,t0

        # carry for ABV_0
        vmv.v.i v30,0

        # loop variable
        li  t5,0

        # load last group of values from arg
        # offset of last group
        # !!! important: assume nreg = 8 and sew = 32
        # log(8) + log(32/8) = 5
        # LOOP2 is now ngroup - 1
        slli t3,t5,5
        add  t3,t3,a0
        vlsseg2e32.v v20, (t3), t1

        # ---
        # propagate (j=0, ngroupreg=2)
        # ---

            vadd.vv v20, v20, v30
        # save carry in TV
        vsrl.vi v30, v20, 16
        # mod 2 ** 16
        vand.vv v20, v20, v31
            vadd.vv v21, v21, v30

        # ---
        # propagate (j=1, ngroupreg=2)
        # ---

        # save carry in TV
        vsrl.vi v30, v21, 16
        # mod 2 ** 16
        vand.vv v21, v21, v31
        # store last group of AB
        vssseg2e32.v v20, (t3), t1

        # update carry of AB_{ntotalreg - 1} to AB_0
        vlse32.v v20, (a0), t1
        vslide1up.vx v31, v30, zero
        vadd.vv v20, v20, v31
        vsse32.v v20, (a0), t1
        addi  t2,t2,1
        li    t0,128
        bne   t2,t0,9b
    # !!!!!! important: lw here assumes SEW = 32
    # T0 is used in vmacc, do not use for temp now!
    lw      t0, 0(a0)
    mul     t0, t0, a4
    # mod 2 ** 16
    # !!!! important: here we assume SEW = 32 and XLEN = 64
    sll    t0, t0, 16
    srl    t0, t0, 16

    # loop variable
    li  t5,0

    # ---
    # macc (V=a3, VV=v0, VVN=0, ngroupreg=2)
    # ---

    # load one group of values from arg
    # offset of one group
    # !!! important: assume nreg = 8 and sew = 32
    # log(8) + log(32/8) = 5
    slli t2,t5,5
    add  t3,t2,a3
    vlsseg2e32.v v0, (t3), t1
    add  t3,t2,a0
    vlsseg2e32.v v20, (t3), t1
        vmacc.vx v20, t0, v0
        vmacc.vx v21, t0, v1
    # store one group of AB
    vssseg2e32.v v20, (t3), t1

        # ---
        # propagate_niter
        # ---

        # start loop of niter + 1 times
        # use T2 as outer loop index
        li  t2,0
    9:
        # mask
        # set TV2 for every propagate()
        # set TV2 every time (see slide1up below)
        li  t0,65535
        vmv.v.x v31,t0

        # carry for ABV_0
        vmv.v.i v30,0

        # loop variable
        li  t5,0

        # load last group of values from arg
        # offset of last group
        # !!! important: assume nreg = 8 and sew = 32
        # log(8) + log(32/8) = 5
        # LOOP2 is now ngroup - 1
        slli t3,t5,5
        add  t3,t3,a0
        vlsseg2e32.v v20, (t3), t1

        # ---
        # propagate (j=0, ngroupreg=2)
        # ---

            vadd.vv v20, v20, v30
        # save carry in TV
        vsrl.vi v30, v20, 16
        # mod 2 ** 16
        vand.vv v20, v20, v31
            vadd.vv v21, v21, v30

        # ---
        # propagate (j=1, ngroupreg=2)
        # ---

        # save carry in TV
        vsrl.vi v30, v21, 16
        # mod 2 ** 16
        vand.vv v21, v21, v31
        # store last group of AB
        vssseg2e32.v v20, (t3), t1

        # update carry of AB_{ntotalreg - 1} to AB_0
        vlse32.v v20, (a0), t1
        vslide1up.vx v31, v30, zero
        vadd.vv v20, v20, v31
        vsse32.v v20, (a0), t1
        addi  t2,t2,1
        li    t0,128
        bne   t2,t0,9b

    # update carry of AB_1 to AB_0
    # since we need to substract AB_0
    vlse32.v v20, (a0), t1
    # AB / word
    vslide1down.vx v30, v20, zero
    # do not need vsse now
    # just store it in TV for move

        # -----
        # move
        # -----

        # move AB_1 to AB_0, AB_2 to AB_1, ... , AB_0 (in TV now) to AB_1
        # loop variable
        li  t5,0
        # load last group of values from arg
        # offset of last group
        # !!! important: assume nreg = 8 and sew = 32
        # log(8) + log(32/8) = 5
        # LOOP2 is now ngroup - 1
        slli t2,t5,5
        # then offset by 1 element
        addi t2,t2,4
        add  t3,t2,a0
        vlse32.v v20, (t3), t1
        # move AB_0 to AB_1
        vmv.v.v v21, v30

        # back to original offset
        addi t3,t3,-4
        vssseg2e32.v v20, (t3), t1

    addi  t4,t4,1
    li    t0,17

    bne   t4,t0,1b

    ret
