#include <stdint.h>

__attribute((section(".vbss"))) int32_t input_A[32768];
__attribute((section(".vbss"))) int32_t input_B[32768];
__attribute((section(".vbss"))) int32_t output[32768];
