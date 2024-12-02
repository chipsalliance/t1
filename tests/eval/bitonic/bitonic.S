.text
.balign 16
.global bitonic

bitonic:
  // a0: `*in`
  // a1: `n`

  vsetvli zero, a1, e32, m8, ta, ma
  vle32.v v8, 0(a0)             // v8-v15[i] = in[i]

  // prepare range
  // v4-v7[i] = i (EEW=16)
  vsetvli zero, a1, e16, m4, ta, ma
  vid.v v4

// for (k = 2; k <= n; k *= 2)
  li t0, 2  // [t0] = k = 2
loop_k:
// for (j = k/2; j > 0; j /= 2)
  srli t1, t0, 1  // [t1] = j = k/2
loop_j:
  vsetvli zero, a1, e16, m4, ta, ma
  vxor.vx v24, v4, t1  // v24-v27[i] = i ^ j

  vsetvli zero, a1, e32, m8, ta, ma
  vrgatherei16.vv v16, v8, v24  // v16-v23[i] = in[i ^ j]
  vmsgt.vv v1, v8, v16  // v1[i] = in[i] < in[i ^ j]

  vsetvli zero, a1, e16, m4, ta, ma
  vand.vx v28, v4, t0  // v28-v31[i] = i & k
  vmsne.vi v2, v28, 0  // v2[i] = (i & k) != 0
  vand.vx v28, v4, t1  // v28-v31[i] = i & j
  vmsne.vi v3, v28, 0  // v3[i] = (i & j) != 0
  vmxor.mm v0, v2, v3   // v0[i] = ((i & k) != 0) ^ ((i & j) != 0)
  vmxor.mm v0, v0, v1   // v0[i] = ((i & k) != 0) ^ ((i & j) != 0) ^ (in[i] < in[i ^ j])

  // swap
  vsetvli zero, a1, e32, m8, ta, ma
  vxor.vv v16, v16, v8  // v16-v23[i] = in[i] ^ in[i ^ j]
  vxor.vv v8, v16, v8, v0.t

  srli t1, t1, 1  // j /= 2
  bnez t1, loop_j

  slli t0, t0, 1  // k *= 2
  ble t0, a1, loop_k

  vse32.v v8, 0(a0)
  
  ret


