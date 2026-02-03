/** softmax.c
 * Manually replicate the safe_softmax.py
 */
#include <math.h>
#include <riscv_vector.h>
#include <stdlib.h>
#ifdef DO_DIFF_TEST
#include <stdio.h>
#endif

#include "model_data.h"

// We are not linking with std lib so this is required
float riscv_sqrtf(float number) {
  float result;
  __asm__ volatile("fsqrt.s %0, %1, dyn" : "=f"(result) : "f"(number));

  return result;
}

void softmax(const float *input, float *output, size_t size, float d_head) {
  size_t avl;
  size_t vl;
  float scale = 1.0f / riscv_sqrtf(d_head);

  size_t vlmax_e32m8 = __riscv_vsetvlmax_e32m8();
  vfloat32m8_t v_max_acc = __riscv_vfmv_v_f_f32m8(-INFINITY, vlmax_e32m8);

  avl = size;
  const float *in_ptr = input;

  // calculate scale and max vertically
  for (; avl > 0; avl -= vl) {
    vl = __riscv_vsetvl_e32m8(avl);
    vfloat32m8_t v_in = __riscv_vle32_v_f32m8(in_ptr, vl);
    vfloat32m8_t v_scaled = __riscv_vfmul_vf_f32m8(v_in, scale, vl);
    v_max_acc = __riscv_vfmax_vv_f32m8_tu(v_max_acc, v_max_acc, v_scaled, vl);
    in_ptr += vl;
  }

  // fold max into v0
  vfloat32m1_t v_max_scalar = __riscv_vfmv_v_f_f32m1(-INFINITY, 1);
  v_max_scalar =
      __riscv_vfredmax_vs_f32m8_f32m1(v_max_acc, v_max_scalar, vlmax_e32m8);
  float max_val = __riscv_vfmv_f_s_f32m1_f32(v_max_scalar);

#ifndef FAST_EXP
  // calculate exp(x - max)
  size_t vlmax_e32m2 = __riscv_vsetvlmax_e32m2();
  // pre-load constants for exp (Horner's Method)
  vfloat32m2_t v_c0 = __riscv_vfmv_v_f_f32m2(1.0f, vlmax_e32m2);
  vfloat32m2_t v_c1 = __riscv_vfmv_v_f_f32m2(1.0f, vlmax_e32m2);
  vfloat32m2_t v_c2 = __riscv_vfmv_v_f_f32m2(0.5f, vlmax_e32m2);
  vfloat32m2_t v_c3 = __riscv_vfmv_v_f_f32m2(1.0f / 6.0f, vlmax_e32m2);
  vfloat32m2_t v_c4 = __riscv_vfmv_v_f_f32m2(1.0f / 24.0f, vlmax_e32m2);
  vfloat32m2_t v_c5 = __riscv_vfmv_v_f_f32m2(1.0f / 120.0f, vlmax_e32m2);
  vfloat32m2_t v_c6 = __riscv_vfmv_v_f_f32m2(1.0f / 720.0f, vlmax_e32m2);
  vfloat32m2_t v_c7 = __riscv_vfmv_v_f_f32m2(1.0f / 5040.0f, vlmax_e32m2);

  vfloat32m2_t v_inv_ln2 =
      __riscv_vfmv_v_f_f32m2(1.4426950408889634f, vlmax_e32m2);
  vfloat32m2_t v_ln2_hi =
      __riscv_vfmv_v_f_f32m2(0.693147180559945f, vlmax_e32m2);
  vfloat32m2_t v_ln2_lo = __riscv_vfmv_v_f_f32m2(1.4286068203e-6f, vlmax_e32m2);
  vfloat32m2_t v_min_lim = __riscv_vfmv_v_f_f32m2(-88.0f, vlmax_e32m2);
  vfloat32m2_t v_zero = __riscv_vfmv_v_f_f32m2(0.0f, vlmax_e32m2);

  // Accumulator for Sum (Init to 0)
  vfloat32m2_t v_sum_acc = __riscv_vfmv_v_f_f32m2(0.0f, vlmax_e32m2);

  avl = size;
  in_ptr = input;
  float *out_ptr = output;

  for (; avl > 0; avl -= vl) {
    vl = __riscv_vsetvl_e32m2(avl);

    vfloat32m2_t v_in = __riscv_vle32_v_f32m2(in_ptr, vl);
    // to remove heavy store operation, the former scale calculation is not
    // store back into input, so we need to calculate again here.
    vfloat32m2_t vx = __riscv_vfmul_vf_f32m2(v_in, scale, vl);

    // subtract max value to have safe exp
    vx = __riscv_vfsub_vf_f32m2(vx, max_val, vl);

    // calculate e_x with -88.0f is meanless, it is approximate to zero, and
    // softmax need no negative value. So here we mask all the value under
    // -88.0f here to avoid overflow.
    vbool16_t mask_underflow = __riscv_vmflt_vv_f32m2_b16(vx, v_min_lim, vl);

    // k = round(x * 1/ln2)
    vfloat32m2_t vz = __riscv_vfmul_vv_f32m2(vx, v_inv_ln2, vl);
    vint32m2_t vk = __riscv_vfcvt_x_f_v_i32m2(vz, vl);
    vfloat32m2_t vkf = __riscv_vfcvt_f_x_v_f32m2(vk, vl);

    // range reduction: r = x - k * ln2
    vfloat32m2_t vr = __riscv_vfmsac_vv_f32m2(vx, vkf, v_ln2_hi, vl);
    vr = __riscv_vfmsac_vv_f32m2(vr, vkf, v_ln2_lo, vl);

    // polynomial approx
    vfloat32m2_t v_poly = __riscv_vfmadd_vv_f32m2(vr, v_c7, v_c6, vl);
    v_poly = __riscv_vfmadd_vv_f32m2(v_poly, vr, v_c5, vl);
    v_poly = __riscv_vfmadd_vv_f32m2(v_poly, vr, v_c4, vl);
    v_poly = __riscv_vfmadd_vv_f32m2(v_poly, vr, v_c3, vl);
    v_poly = __riscv_vfmadd_vv_f32m2(v_poly, vr, v_c2, vl);
    v_poly = __riscv_vfmadd_vv_f32m2(v_poly, vr, v_c1, vl);
    v_poly = __riscv_vfmadd_vv_f32m2(v_poly, vr, v_c0, vl);

    // Reconstruct 2^k
    vint32m2_t v_exp_i = __riscv_vadd_vx_i32m2(vk, 127, vl);
    v_exp_i = __riscv_vsll_vx_i32m2(v_exp_i, 23, vl);
    vfloat32m2_t v_2k = __riscv_vreinterpret_v_i32m2_f32m2(v_exp_i);

    // e_x = 2_k * e_r
    vfloat32m2_t v_final = __riscv_vfmul_vv_f32m2(v_poly, v_2k, vl);

    // apply the mask for x < -88.0f
    v_final = __riscv_vmerge_vvm_f32m2(v_final, v_zero, mask_underflow, vl);

    // accumulate the exp sum now to avoid extra load store
    v_sum_acc = __riscv_vfadd_vv_f32m2_tu(v_sum_acc, v_sum_acc, v_final, vl);

    __riscv_vse32_v_f32m2(out_ptr, v_final, vl);

    in_ptr += vl;
    out_ptr += vl;
  }

  // get the sum(exp(v0..n))
  vfloat32m1_t v_sum_scalar = __riscv_vfmv_v_f_f32m1(0.0f, 1);
  v_sum_scalar =
      __riscv_vfredusum_vs_f32m2_f32m1(v_sum_acc, v_sum_scalar, vlmax_e32m2);
#else // FAST_EXP
  avl = size;
  in_ptr = input;
  float *out_ptr = output;

  // Accumulator for Sum (Init to 0)
  vfloat32m8_t v_sum_acc = __riscv_vfmv_v_f_f32m8(0.0f, vlmax_e32m8);

  for (; avl > 0; avl -= vl) {
    vl = __riscv_vsetvl_e32m8(avl);
    vfloat32m8_t v_in = __riscv_vle32_v_f32m8(in_ptr, vl);
    // subtract max value to have safe exp
    vfloat32m8_t vx = __riscv_vfsub_vf_f32m8(v_in, max_val, vl);
    // simulate a fast exp call
    vx = __riscv_vfmul_vf_f32m8(vx, scale, vl);
    v_sum_acc = __riscv_vfadd_vv_f32m8_tu(v_sum_acc, v_sum_acc, vx, vl);
    __riscv_vse32_v_f32m8(out_ptr, vx, vl);

    in_ptr += vl;
    out_ptr += vl;
  }

  vfloat32m1_t v_sum_scalar = __riscv_vfmv_v_f_f32m1(0.0f, 1);
  v_sum_scalar =
      __riscv_vfredusum_vs_f32m8_f32m1(v_sum_acc, v_sum_scalar, vlmax_e32m8);
#endif // FAST_EXP

  float sum_val = __riscv_vfmv_f_s_f32m1_f32(v_sum_scalar);
  // return the softmax value
  float inv_sum = 1.0f / sum_val;
  avl = size;
  out_ptr = output;

  for (; avl > 0; avl -= vl) {
    vl = __riscv_vsetvl_e32m8(avl);
    vfloat32m8_t v_out = __riscv_vle32_v_f32m8(out_ptr, vl);
    v_out = __riscv_vfmul_vf_f32m8(v_out, inv_sum, vl);
    __riscv_vse32_v_f32m8(out_ptr, v_out, vl);
    out_ptr += vl;
  }
}

#ifdef DO_DIFF_TEST
// Speed is not the case when running diff, so I use tail undisturbed strategy
// here.
float diff_tensor(const float *left, const float *right, size_t size) {
  size_t vl;
  size_t avl = size;
  size_t vlmax = __riscv_vsetvlmax_e32m8();
  vfloat32m8_t v_max_acc = __riscv_vfmv_v_f_f32m8(0, vlmax);

  for (; avl > 0; avl -= vl) {
    vl = __riscv_vsetvl_e32m8(avl);
    vfloat32m8_t v_left = __riscv_vle32_v_f32m8(left, vl);
    vfloat32m8_t v_right = __riscv_vle32_v_f32m8(right, vl);

    v_left = __riscv_vfsub_vv_f32m8(v_left, v_right, vl);
    vfloat32m8_t v_abs = __riscv_vfabs_v_f32m8(v_left, vl);
    v_max_acc = __riscv_vfmax_vv_f32m8_tu(v_max_acc, v_max_acc, v_abs, vl);

    left += vl;
    right += vl;
  }

  vfloat32m1_t v_max = __riscv_vfmv_v_f_f32m1(0.0f, 1);
  v_max = __riscv_vfredmax_vs_f32m8_f32m1(v_max_acc, v_max, vlmax);
  return __riscv_vfmv_f_s_f32m1_f32(v_max);
}
#endif

void test() {
  // All the variable here is generated by ./safe_softmax.py.
  // Runs python3 safe_softmax.py dump to reproduce the header file
  int batch_size = input_tensor_shape[0];
  int row_size = input_tensor_shape[1];

  for (int i = 0; i < batch_size; i++) {
    const float *current = input_tensor + (i * row_size);
    float *scratch_pad = output_tensor + (i * row_size);
    softmax(current, scratch_pad, row_size, d_head);
  }

#ifdef DO_DIFF_TEST
  // reference_tensor is generated using Pytorch softmax in torch.nn.functional
  // module.
  float max_diff =
      diff_tensor(output_tensor, reference_tensor, input_tensor_len);
  if (max_diff < 1e-6) {
    printf("Status: Matches reference\n");
  } else {
    printf("Status: Difference too large: %f\n", max_diff);
  }
#endif
}
