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

#define DEFINE_CONV(name, OH, OW, CO, CI, K) \
  const int name##_OH = (OH); \
  const int name##_OW = (OW); \
  const int name##_CO = (CO); \
  const int name##_CI = (CI); \
  const int name##_K = (K); \
  VDATA int32_t name##_input[(CI)*((OH)+(K)-1)*((OW)+(K)-1)]; \
  VDATA int32_t name##_output[(CO)*(OH)*(OW)]; \
  VDATA int32_t name##_kernel[(CO)*(CI)*(K)*(K)];

#define RUN_CONV(name) conv2d(name##_output, name##_input, name##_kernel, name##_OH, name##_OW, name##_CO, name##_CI, name##_K)

// CONV1: O[224, 224, 64], W[64, 3, 7, 7]
// VDATA int32_t conv1_img[3*230*230];
// VDATA int32_t conv1_output[64*224*224];
// VDATA int32_t conv1_kernel[64*3*7*7];

DEFINE_CONV(conv1, 112, 112, 64, 3, 7)

DEFINE_CONV(conv2, 56, 56, 64, 64, 3)
DEFINE_CONV(conv3, 56, 56, 64, 64, 3)

DEFINE_CONV(conv4, 56, 56, 64, 64, 3)
DEFINE_CONV(conv5, 56, 56, 64, 64, 3)

DEFINE_CONV(conv6, 28, 28, 128, 64, 3)
DEFINE_CONV(conv7, 28, 28, 128, 128, 3)
// +
DEFINE_CONV(conv8, 28, 28, 128, 64, 3)

DEFINE_CONV(conv9, 28, 28, 128, 128, 3)
DEFINE_CONV(conv10, 28, 28, 128, 128, 3)


DEFINE_CONV(conv11, 14, 14, 256, 128, 3)
DEFINE_CONV(conv12, 14, 14, 256, 256, 3)
// +
DEFINE_CONV(conv13, 14, 14, 256, 128, 3)

DEFINE_CONV(conv14, 14, 14, 256, 256, 3)
DEFINE_CONV(conv15, 14, 14, 256, 256, 3)


DEFINE_CONV(conv16, 7, 7, 512, 256, 3)
DEFINE_CONV(conv17, 7, 7, 512, 512, 3)
// +
DEFINE_CONV(conv18, 7, 7, 512, 256, 3)

DEFINE_CONV(conv19, 7, 7, 512, 512, 3)
DEFINE_CONV(conv20, 7, 7, 512, 512, 3)


int test() {
  RUN_CONV(conv1);
  RUN_CONV(conv2);
  RUN_CONV(conv3);
  RUN_CONV(conv4);
  RUN_CONV(conv5);
  RUN_CONV(conv6);
  RUN_CONV(conv7);
  RUN_CONV(conv8);
  RUN_CONV(conv9);
  RUN_CONV(conv10);
  RUN_CONV(conv11);
  RUN_CONV(conv12);
  RUN_CONV(conv13);
  RUN_CONV(conv14);
  RUN_CONV(conv15);
  RUN_CONV(conv16);
  RUN_CONV(conv17);
  RUN_CONV(conv18);
  RUN_CONV(conv19);
  RUN_CONV(conv20);

  return 0;
}
