// FastGEMM RVV C intrinsics implementation.

#include <riscv_vector.h>
#define min(a,b) (int)((a) < (b) ? (a) : (b))

__attribute((section(".vbss"))) int A[64];
__attribute((section(".vbss"))) int B[64];
__attribute((section(".vbss"))) int C[64];

void test() {

  int *aptr=A;
  int *bptr=B;
  int *cptr=C;
  int ma=64;
  int na=64;
  int nb=64;
  int avl = nb, vl;
  size_t astep = 64;
  size_t bstep = 64;
  size_t cstep = 64;
  for (int n = 0; n < nb; n += vl, avl -= vl) {
    vl = __riscv_vsetvl_e32m4(avl);
    for (int m = 0; m < ma; m += 7) {
      const int *aptr0 = aptr + astep * m;
      const int *aptr1 = aptr + astep * min(m + 1, ma - 1);
      const int *aptr2 = aptr + astep * min(m + 2, ma - 1);
      const int *aptr3 = aptr + astep * min(m + 3, ma - 1);
      const int *aptr4 = aptr + astep * min(m + 4, ma - 1);
      const int *aptr5 = aptr + astep * min(m + 5, ma - 1);
      const int *aptr6 = aptr + astep * min(m + 6, ma - 1);

      int *cptr0 = cptr + cstep * m;
      int *cptr1 = cptr + cstep * min(m + 1, ma - 1);
      int *cptr2 = cptr + cstep * min(m + 2, ma - 1);
      int *cptr3 = cptr + cstep * min(m + 3, ma - 1);
      int *cptr4 = cptr + cstep * min(m + 4, ma - 1);
      int *cptr5 = cptr + cstep * min(m + 5, ma - 1);
      int *cptr6 = cptr + cstep * min(m + 6, ma - 1);

      vint32m4_t d0 = __riscv_vmv_v_x_i32m4(0, vl);
      vint32m4_t d1 = __riscv_vmv_v_x_i32m4(0, vl);
      vint32m4_t d2 = __riscv_vmv_v_x_i32m4(0, vl);
      vint32m4_t d3 = __riscv_vmv_v_x_i32m4(0, vl);
      vint32m4_t d4 = __riscv_vmv_v_x_i32m4(0, vl);
      vint32m4_t d5 = __riscv_vmv_v_x_i32m4(0, vl);
      vint32m4_t d6 = __riscv_vmv_v_x_i32m4(0, vl);

      for (int k = 0; k < na; k++) {
        int a0 = aptr0[k];
        int a1 = aptr1[k];
        int a2 = aptr2[k];
        int a3 = aptr3[k];
        int a4 = aptr4[k];
        int a5 = aptr5[k];
        int a6 = aptr6[k];

        vint32m4_t b = __riscv_vle32_v_i32m4(bptr + k * bstep + n, vl);
        d0 = __riscv_vmacc_vx_i32m4(d0, a0, b, vl);
        d1 = __riscv_vmacc_vx_i32m4(d1, a1, b, vl);
        d2 = __riscv_vmacc_vx_i32m4(d2, a2, b, vl);
        d3 = __riscv_vmacc_vx_i32m4(d3, a3, b, vl);
        d4 = __riscv_vmacc_vx_i32m4(d4, a4, b, vl);
        d5 = __riscv_vmacc_vx_i32m4(d5, a5, b, vl);
        d6 = __riscv_vmacc_vx_i32m4(d6, a6, b, vl);
      }

      __riscv_vse32_v_i32m4(cptr0 + n, d0, vl);
      __riscv_vse32_v_i32m4(cptr1 + n, d1, vl);
      __riscv_vse32_v_i32m4(cptr2 + n, d2, vl);
      __riscv_vse32_v_i32m4(cptr3 + n, d3, vl);
      __riscv_vse32_v_i32m4(cptr4 + n, d4, vl);
      __riscv_vse32_v_i32m4(cptr5 + n, d5, vl);
      __riscv_vse32_v_i32m4(cptr6 + n, d6, vl);
    }
  }
}
