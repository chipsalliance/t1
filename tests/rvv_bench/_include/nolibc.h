#pragma once

#include <float.h>
#include <limits.h>
#include <stddef.h>
#include <stdint.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if __riscv_xlen == 32
typedef uint32_t ux;
typedef float fx;
#define IF64(...)
#elif __riscv_xlen == 64
typedef uint64_t ux;
typedef double fx;
#define IF64(...) __VA_ARGS__
#else
#error "unsupported XLEN"
#endif
#define ARR_LEN(x) (sizeof x / sizeof *(x))

static void memwrite(void const *ptr, size_t len) {
  fwrite(ptr, 1, len, stdout);
}

static size_t memread(void *ptr, size_t len) {
  return fread(ptr, 1, len, stdin);
}

static inline ux rv_cycles(void) {
  ux cycle;
  __asm volatile("csrr %0, mcycle" : "=r"(cycle));
  return cycle;
}

static void memswap(void *a, void *b, size_t size) {
  unsigned char *A = (unsigned char *)a, *B = (unsigned char *)b;
  unsigned char *aEnd = A + size;
  while (A < aEnd) {
    unsigned char temp = *A;
    *A++ = *B;
    *B++ = temp;
  }
}

static ux usqrt(ux y) {
  ux L = 0, R = y + 1;
  while (L != R - 1) {
    ux M = (L + R) / 2;
    if (M * M <= y)
      L = M;
    else
      R = M;
  }
  return L;
}

static ux uhash(ux x) {
#if __riscv_xlen == 32
  /* MurmurHash3 32-bit finalizer */
  x ^= x >> 16;
  x *= 0x85ebca6b;
  x ^= x >> 13;
  x *= 0xc2b2ae35;
  x ^= x >> 16;
#else
  /* splitmix64 finalizer */
  x ^= x >> 30;
  x *= 0xbf58476d1ce4e5b9U;
  x ^= x >> 27;
  x *= 0x94d049bb133111ebU;
  x ^= x >> 31;
#endif
  return x;
}

#define IFHOSTED(...) __VA_ARGS__
