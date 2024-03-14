// #include "rvv.h"
#include <riscv_vector.h>
#include <stddef.h>
#include <stdint.h>

typedef int32_t vl_type;

// when AVL >= MAXVL, this is efficent
void conv2d(int32_t *restrict output, int32_t const *restrict img,
            int32_t const *restrict kernel, size_t imgRow, size_t imgCol,
            size_t kernelSize) {

  size_t const outRow = imgRow - kernelSize + 1;
  size_t const outCol = imgCol - kernelSize + 1;

  for (size_t iI = 0; iI < imgRow; iI++) {
    for (size_t kI = 0; kI < kernelSize; kI++) {
      // only need img[kI] to img[imgRow + kI - kernelSize]
      if (!(kI <= iI && iI < kI + outCol))
        continue;

      for (size_t kJ = 0; kJ < kernelSize; kJ++) {
        int32_t const K = kernel[kI * kernelSize + kJ];
        // from img[iI][kJ] to img[iI][imgCol - kernelSize], step by 1
        // imgCol - kernelSize + 1 is the number of elements to be processed
        int32_t const *imgPtr = img + iI * imgCol + kJ;
        // from output[iI - kI][0] to output[iI - kI][imgCol - kernelSize],
        int32_t *outPtr = output + (iI - kI) * outCol;
        // when AVL >= MAXVL, this is efficent
        size_t avl = imgCol - kernelSize + 1;
        while (avl > 0) {
          // TODO: exchange vl-loop and kJ loop, can be more cache friendly

          size_t vl = __riscv_vsetvl_e32m2(avl);

          vint32m2_t imgVec = __riscv_vle32_v_i32m2(imgPtr, vl);
          vint32m2_t mulVec = __riscv_vmul_vx_i32m2(imgVec, K, vl);

          vint32m2_t outVec = __riscv_vle32_v_i32m2(outPtr, vl);
          vint32m2_t resVec = __riscv_vadd_vv_i32m2(outVec, mulVec, vl);

          __riscv_vse32_v_i32m2(outPtr, resVec, vl);

          avl -= vl;
          imgPtr += vl;
          outPtr += vl;
        }
      }
    }
  }
}

__attribute((section(".vdata"))) int img[7][7] = {
  {0, 1, 2, 3, 4, 5, 6},
  {7, 8, 9, 10, 11, 12, 13},
  {14, 15, 16, 17, 18, 19, 20},
  {21, 22, 23, 24, 25, 26, 27},
  {28, 29, 30, 31, 32, 33, 34},
  {35, 36, 37, 38, 39, 40, 41},
  {42, 43, 44, 45, 46, 47, 48},
};

__attribute((section(".vdata"))) int kernel[3][3] = {
  {1, 2, 3},
  {4, 5, 6},
  {7, 8, 9},
};

__attribute((section(".vbss"))) int output[5][5];

int test() {
  conv2d((int32_t*) output, (int32_t const*) img, (int32_t const*) kernel, 7, 7, 3);

  return output[1][2];
}
