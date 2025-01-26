#include <stdio.h>
#include <assert.h>

// #define USE_SCALAR
// #define DEBUG
// #define WITHMAIN

#ifdef ntt_64
  // requires VLEN >= 512
  #include "ntt_64.h"
#endif

#ifdef ntt_128
  // requires VLEN >= 512
  #include "ntt_128.h"
#endif

#ifdef ntt_256
  // requires VLEN >= 1024
  #include "ntt_256.h"
#endif

#ifdef ntt_512
  // requires VLEN >= 2048
  #include "ntt_512.h"
#endif

#ifdef ntt_1024
  // requires VLEN >= 4096
  #include "ntt_1024.h"
#endif

#ifdef ntt_4096
  #include "ntt_4096.h"
#endif


void ntt(const int *array, int l, const int *twiddle, int p, int *dst);

void test() {
  const int l = macroL;
  const int n = macroN;
  static const int arr[macroN] = {
    macroIn
  };
#ifdef USE_SCALAR
  static const int twiddle[] = {
     macroScalarTW
  };
#else
  static const int twiddle[] = {
    macroVectorTW
  };
#endif
  const int p = macroP;
  int dst[macroN];
  ntt(arr, l, twiddle, p, dst);

#ifdef DEBUG
  const int gold[macroN] = {
    macroOut
  };
  printf("n = %d\n", n);
  for (int i = 0; i < n; i++) {
    // dst[i] = dst[i] % p;
    if(dst[i] < 0) dst[i] += p;
    if(dst[i] != gold[i]) {
      printf("(%d %d, i)", dst[i], gold[i], i);
      if ((i + 1) % 8 == 0) {
        printf("\n");
      } else {
        printf(" ");
      }
    }
  }
#endif
}

#ifdef WITHMAIN
int main(void) {
  test();
}
#endif

