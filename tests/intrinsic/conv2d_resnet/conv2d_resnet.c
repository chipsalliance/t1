#include <riscv_vector.h>
#include <stddef.h>
#include <stdint.h>

typedef int32_t vl_type;

// Adapt from conv2d_less_m2, add outChannel and inChannel
//
// when AVL >= MAXVL, this is efficent
void conv2d(int32_t *restrict output_, int32_t const *restrict img_,
            int32_t const *restrict kernel_, size_t imgRow, size_t imgCol,
            size_t outChannel, size_t inChannel, size_t kernelSize) {

  size_t const outRow = imgRow - kernelSize + 1;
  size_t const outCol = imgCol - kernelSize + 1;

  for (size_t coI = 0; coI < outChannel; coI++) {
    for (size_t ciI = 0; ciI < inChannel; ciI++) {
      int32_t *output = output_ + coI * (outRow * outCol);
      int32_t const *img = img_ + ciI * (imgRow * imgCol);
      int32_t const *kernel = kernel_ + (coI * inChannel + ciI) * kernelSize * kernelSize;

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
  }
}

#define VDATA __attribute((section(".vdata")))
#define VBASS __attribute((section(".vbss")))

// size of image is padded to (H+K-1, W+K-1)

// CONV1: O[224, 224, 64], W[64, 3, 7, 7]
VDATA int32_t conv1_img[64*230*230];
VDATA int32_t conv1_output[64*224*224];
VDATA int32_t conv1_kernel[64*3*7*7];

// CONV2: ...

int test() {
  conv2d(conv1_output, conv1_img, conv1_kernel, 224, 224, 64, 3, 7);

  // conv2d(...)

  return 0;
}
