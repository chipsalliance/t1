#include <stdint.h>

__attribute((section(".vdata"))) int32_t gv_input_i32 [128][128];
__attribute((section(".vdata"))) int32_t gv_output_i32 [128][128];
__attribute((section(".vdata"))) int32_t gv_kernel_i32 [3][3] = {
  { 1, 2, 1 },
  { 2, 4, 2 },
  { 1, 2, 1 },
};

