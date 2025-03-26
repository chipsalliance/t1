#include "memref.hpp"

#define INPUT_N 1
#define INPUT_C 1
#define INPUT_H 28
#define INPUT_W 28
#define INPUT_TOTAL (INPUT_N * INPUT_C * INPUT_H * INPUT_W)
#define OUTPUT_N 10
#define PARAM_N 44426

__attribute((section(".vdata"))) float input_0[INPUT_TOTAL];
__attribute((section(".vdata"))) float output_0[OUTPUT_N];
__attribute((section(".vdata"))) float param_0[PARAM_N];

// Define the sizes of the input and output tensors.
// static const int32_t sizesInput[4] = {INPUT_N, INPUT_C, INPUT_H, INPUT_W};
// static const int32_t sizesOutput[2] = {1, OUTPUT_N};
// static const int32_t sizesParams[1] = {PARAM_N};

// Create input and output containers for the image and model output.
// MemRef<float, 4> input(input_0, sizesInput);
// MemRef<float, 2> output(output_0, sizesOutput);
// MemRef<float, 1> params(param_0, 2.0, sizesParams);

// Declare the target model C interface.
extern "C" {
void _mlir_ciface_forward(MemRef<float, 2> *output, MemRef<float, 1> *arg0,
                          MemRef<float, 4> *input);
}

extern "C" int test() {
  int32_t sizesInput[4] = {INPUT_N, INPUT_C, INPUT_H, INPUT_W};
  int32_t sizesOutput[2] = {1, OUTPUT_N};
  int32_t sizesParams[1] = {PARAM_N};
  MemRef<float, 4> input(input_0, sizesInput);
  MemRef<float, 2> output(output_0, sizesOutput);
  MemRef<float, 1> params(param_0, 2.0, sizesParams);
  _mlir_ciface_forward(&output, &params, &input);
  return 0;
}
