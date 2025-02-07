#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

int math_mod(int num, int p) {
  return num % p;
}

// array is of length n=2^l, p is a prime number
// roots is of length l, where g = roots[0] satisfies that
//   g^(2^l) == 1 mod p  and g^(2^(l-1)) == -1 mod p
// roots[i] = g^(2^i)  (hence roots[l - 1] = -1)
void ntt(const int *array, int l, const int *twiddle, int p, int *dst) {
  // prepare an array of permutation indices
  assert(l <= 16);

  int n = 1 << l;

  // permutate the array to dst
  // first abuse dst to store permutation inde
  // i.e. buf[i] = reversed binary representation of i
  dst[0] = 0;
  for (int i = 1; i < n; ++i) {
    dst[i] = dst[i >> 1] >> 1;
    if (i & 1) {
      dst[i] |= n >> 1;
    }
  }

  // then perform the permutation
  for (int i = 0; i < n; ++i) {
    int i_rev = dst[i];
    if (i >= i_rev) {
      dst[i] = array[i_rev];
      dst[i_rev] = array[i];
    }
  }

  // perform butterfly operations
  int w_m_index = l - 1;
  for (int m = 2; m <= n; m <<= 1) { // totally l stages
    int w_m = twiddle[w_m_index];  
    w_m_index -= 1;
    int w = 1;
    for (int j = 0; j < m/2; j++) {
      for (int k = 0; k < n; k += m) {
        int index_u = k + j;
        int index_t = k + j + (m >> 1);
        int t = math_mod(w * dst[index_t], p);
        int u = dst[index_u];
        dst[index_u] = math_mod(u + t, p);
        dst[index_t] = math_mod(u - t, p);
      }
      w = math_mod(w * w_m, p);
    }
  }
}
