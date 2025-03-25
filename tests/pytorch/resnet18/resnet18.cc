#include "arg0.inc"
#include "generated-img.inc"
#include "memref.hpp"

// Declare the resnet C interface.
extern "C" void _mlir_ciface_forward(MemRef<float, 2> *output,
                                     MemRef<float, 1> *arg0,
                                     MemRef<float, 4> *input);

static const int32_t sizes[2] = {1, 1000};
__attribute((section(".vdata"))) float output_float_1[1000];
MemRef<float, 2> output(output_float_1, sizes);

MemRef<float, 4> inputResize(IMAGE, IMAGE_SIZES);
MemRef<float, 1> paramsContainer(PARAMS, PARAMS_SIZES);

extern "C" int test() {
  _mlir_ciface_forward(&output, &paramsContainer, &inputResize);
  return 0;
}
