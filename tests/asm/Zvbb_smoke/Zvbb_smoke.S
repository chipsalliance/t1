.global test
test:
    addi x1, x1, 1

    li a0, 0x400                  
    li a1, 0xc1                                           
    vsetvl x0, a0, a1

    lui x30, 1
    auipc x31, 0

    vadd.vi v0, v0, 10               
    vadd.vi v2, v0, 11, v0.t

Vector_And_Not_test:        
    vandn.vv v4, v0, v2
    vandn.vx v6, v2, x30

Vector_Reverse_Bits_test:
    vbrev.v v8, v2
    vbrev8.v v10, v2                   
    vrev8.v v12, v2

Vector_Count_test:                   
    vclz.v v14, v2                   
    vctz.v v16, v2                   
    vcpop.v v18, v2

Vector_Rotate_test:                  
    vrol.vv v20, v4, v2
    vrol.vx v22, v2, a2
    vror.vv v24, v4, v2
    vror.vx v26, v4, a2
    vror.vi v28, v4, 3

cycle_count:
    csrr t0, mcycle

    ret

will_not_be_executed:
    vadd.vv v2, v1, v1        
