#include "memref.h"

NEW_MEMREF(float, 1);

extern void _mlir_ciface_forward(struct MemRef_float_dim1 *output,
                                 struct MemRef_float_dim1 *arg1,
                                 struct MemRef_float_dim1 *arg2);

__attribute((section(".vdata"))) float input_float_0[512] = {1, 2, 3};
struct MemRef_float_dim1 input1 = {
    .allocatedPtr = input_float_0,
    .alignedPtr = input_float_0,
    .offset = 0,
    .sizes = {512},
    .strides = {1},
};

__attribute((section(".vdata"))) float input_float_1[512] = {4, 5, 6};
struct MemRef_float_dim1 input2 = {
    .allocatedPtr = input_float_1,
    .alignedPtr = input_float_1,
    .offset = 0,
    .sizes = {512},
    .strides = {1},
};

__attribute((section(".vdata"))) float output_float_0[512];
struct MemRef_float_dim1 output = {
    .allocatedPtr = output_float_0,
    .alignedPtr = output_float_0,
    .offset = 0,
    .sizes = {512},
    .strides = {1},
};

int test() {
  _mlir_ciface_forward(&output, &input1, &input2);
  return 0;
}
