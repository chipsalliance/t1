#include <buddy/Core/Container.h>

// Declare the resnet C interface.
extern "C" void _mlir_ciface_forward(MemRef<float, 2> *output,
                                     MemRef<float, 1> *arg0,
                                     MemRef<float, 4> *input);

__attribute((section(".vdata"))) float output_float_1[1000];
__attribute((section(".vdata"))) float IMAGE[30000];
__attribute((section(".vdata"))) float PARAMS[100];

extern "C" int test() {
  static int32_t sizes[2] = {1, 1000};
  static int32_t params_sizes[1] = {100};
  static int32_t image_sizes[4] = {1, 3, 100, 100};

  MemRef<float, 2> output(output_float_1, sizes);

  MemRef<float, 4> inputResize(IMAGE, image_sizes);
  MemRef<float, 1> paramsContainer(PARAMS, params_sizes);

  _mlir_ciface_forward(&output, &paramsContainer, &inputResize);
  return 0;
}
