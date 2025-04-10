#include <buddy/Core/Container.h>

// Declare the resnet C interface.
extern "C" void _mlir_ciface_forward(MemRef<float, 2> *output,
                                     MemRef<float, 1> *arg0,
                                     MemRef<float, 4> *input);

__attribute((section(".vdata"))) float output_float_1[1000];
__attribute((section(".vdata"))) float IMAGE[150528];
__attribute((section(".vdata"))) float PARAMS[11699112];

extern "C" int test() {
  static int32_t sizes[2] = {1, 1000};
  static int32_t params_sizes[1] = {11699112};
  static int32_t image_sizes[4] = {1, 3, 224, 224};

  MemRef<float, 2> output(output_float_1, 7.0, sizes);

  MemRef<float, 4> inputResize(IMAGE, 6.0, image_sizes);
  MemRef<float, 1> paramsContainer(PARAMS, 5.0, params_sizes);

  _mlir_ciface_forward(&output, &paramsContainer, &inputResize);
  return 0;
}
