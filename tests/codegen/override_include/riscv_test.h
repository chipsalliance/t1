#include_next "riscv_test.h"

#undef EXTRA_DATA

#define EXTRA_DATA \
        .section .vdata, "aw", @progbits; \

