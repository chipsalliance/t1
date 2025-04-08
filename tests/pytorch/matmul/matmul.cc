#include <buddy/Core/Container.h>

extern "C" void _mlir_ciface_forward(MemRef<float, 1> *output,
                                     MemRef<float, 1> *arg1,
                                     MemRef<float, 1> *arg2);

__attribute((section(".vdata"))) float input_float_1[512];
__attribute((section(".vdata"))) float input_float_2[512];
__attribute((section(".vdata"))) float output_float_1[512];

extern "C" int test() {
  // One-dimension, with length 512
  static int32_t sizes[3] = {8, 8, 8};

  MemRef<float, 1> input1(input_float_1, sizes);
  MemRef<float, 1> input2(input_float_2, sizes);
  MemRef<float, 1> output(output_float_1, sizes);

  _mlir_ciface_forward(&output, &input1, &input2);

  return 0;
}
