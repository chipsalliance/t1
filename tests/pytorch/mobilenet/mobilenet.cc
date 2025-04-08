#include "img.hpp"

#define INPUT_N 1
#define INPUT_C 3
#define INPUT_H 224
#define INPUT_W 224
#define INPUT_TOTAL (INPUT_N * INPUT_C * INPUT_H * INPUT_W)
#define OUTPUT_N 1000
// #define PARAM_N0 2554968
#define PARAM_N0 25549
#define PARAM_N1 34

__attribute((section(".vdata"))) float input_0[INPUT_TOTAL];
__attribute((section(".vdata"))) float output_0[OUTPUT_N];
__attribute((section(".vdata"))) float param_0[PARAM_N0];
__attribute((section(".vdata"))) int64_t param_1[PARAM_N1];

extern "C" {
void _mlir_ciface_forward(MemRef<float, 2> *output, MemRef<float, 1> *arg0,
                          MemRef<int64_t, 1> *arg1, Image<float, 4> *input);
}

extern "C" int test() {
  // Define the sizes of the input and output tensors.
  static int32_t sizesInput[4] = {INPUT_N, INPUT_C, INPUT_H, INPUT_W};
  static int32_t sizesOutput[2] = {1, OUTPUT_N};
  static int32_t sizesParam0[1] = {PARAM_N0};
  static int32_t sizesParam1[1] = {PARAM_N1};

  // Generate input memref container with random numbers.
  const int inputSize = INPUT_N * INPUT_C * INPUT_H * INPUT_W;

  // Create input and output containers for the image and model output.
  Image<float, 4> input(input_0, sizesInput);
  MemRef<float, 2> output(output_0, sizesOutput);

  // Set random model parameters.
  MemRef<float, 1> paramsF32(param_0, 2.0, sizesParam0);
  MemRef<int64_t, 1> paramsI64(param_1, 1, sizesParam1);

  _mlir_ciface_forward(&output, &paramsF32, &paramsI64, &input);

  return 0;
}
