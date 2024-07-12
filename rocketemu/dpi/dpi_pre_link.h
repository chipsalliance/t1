// This header provides seveal functions to be used in Rust
//
// dpi_pre_link should be linked before libverilated.so because in
// uses symbols in libverilated.so

#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int verilator_main_c(int argc, char **argv);

#ifdef VM_TRACE
void dump_wave_c(char *path);
#endif

uint64_t get_t_c();

#ifdef __cplusplus
}
#endif
