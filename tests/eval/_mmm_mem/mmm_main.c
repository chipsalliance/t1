#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#ifndef LEN
  // define then error, to make clangd happy without configuration
  #define LEN 1024
  #error "LEN not defined"
#endif

void mmm(uint32_t* r, const uint32_t* a, const uint32_t* b, const uint32_t* p, const uint32_t mu);

void test() {
  int words = (LEN) / 16 + 4;
  uint32_t *r = (uint32_t *) malloc(words * sizeof(uint32_t));
  uint32_t *a = (uint32_t *) malloc(words * sizeof(uint32_t));
  uint32_t *b = (uint32_t *) malloc(words * sizeof(uint32_t));
  uint32_t *p = (uint32_t *) malloc(words * sizeof(uint32_t));
  uint32_t mu = 0xca1b;
  mmm(r, a, b, p, mu);
  // for (int i = 0; i < words; i++) {
  //   printf("%04X ", r[i]);
  // }
}

void main() { test(); }
