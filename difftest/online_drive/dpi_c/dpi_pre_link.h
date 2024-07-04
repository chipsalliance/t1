// This header provides seveal functions to be used in Rust
//
// dpi_pre_link should be linked before libverilated.so because in
// uses symbols in libverilated.so

#pragma once

#ifdef __cplusplus
extern "C" {
#endif

int verilator_main(int argc, char **argv);

void dump_wave(char *path);

void init_wave();

#ifdef __cplusplus
}
#endif
