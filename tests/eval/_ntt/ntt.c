#include <assert.h>
#include <stdio.h>

// #define USERN 32
// #define DEBUG

// array is of length n=2^l, p is a prime number
// roots is of length l, where g = roots[0] satisfies that
//   g^(2^l) == 1 mod p  and g^(2^(l-1)) == -1 mod p
// roots[i] = g^(2^i)  (hence roots[l - 1] = -1)
//
// 32bit * n <= VLEN * 8 => n <= VLEN / 4
void ntt(const int *array, int l, const int *twiddle, int p, int *dst) {
  // prepare an array of permutation indices
  assert(l <= 16);

  int n = 1 << l;

  // registers:
  // v8-15: array
  // v16-24: loaded elements (until vrgather)
  // v4-7: permutation index (until vrgather)
  // v16-24: coefficients
  int vlenb;
  asm("csrr %0, vlenb" : "=r"(vlenb));
  int elements_in_vreg = vlenb * 2;
  assert(elements_in_vreg >= n);

  asm("vsetvli zero, %0, e16, m4, tu, mu\n"
      "vid.v v4\n"
      :
      : "r"(n));

  // prepare the bit-reversal permutation list
  for (int k = 0; 2 * k < l; k++) {
    asm("vand.vx v8, v4, %0\n"
        "vsub.vv v4, v4, v8\n"
        "vsll.vx v8, v8, %1\n" // get the k-th digit and shift left

        "vand.vx v12, v4, %2\n"
        "vsub.vv v4, v4, v12\n"
        "vsrl.vx v12, v12, %1\n" // get the (l-k-1)-th digit and shift right

        "vor.vv v4, v4, v8\n"
        "vor.vv v4, v4, v12\n"

        :
        : "r"(1 << k), "r"(l - 1 - 2 * k), "r"(1 << (l - k - 1)));
  }

  // perform bit-reversal for input coefficients
  asm("vsetvli zero, %0, e32, m8, tu, mu\n"
      "vle32.v v16, 0(%1)\n"
      "vrgatherei16.vv v8, v16, v4\n"
      "vse32.v v8, 0(%2)\n"

      :
      : "r"(n), "r"(array), "r"(dst));

  // generate permutation list (0, 2, 4, ..., 1, 3, 5, ...) 
  asm("vsetvli zero, %0, e16, m4, tu, mu\n"
      "vid.v v4\n"
      "vsrl.vx v0, v4, %1\n" // (0, 0, 0, 0, ..., 1, 1, 1, 1, ...)
      "vand.vx v4, v4, %2\n" // (0, 1, 2, 3, ..., 0, 1, 2, 3, ...)
      "vsll.vi v4, v4, 1\n"
      "vadd.vv v4, v4, v0\n"

      :
      : "r"(n), "r"(l-1), "r"((n / 2 - 1)), "r"(n / 2));

#ifdef DEBUG
  int tmp1[USERN];// c
  int tmp2[USERN];// c
  int tmp3[USERN];// c
#endif

  for (int k = 0; k < l; k++) {
    asm(
        // "n" mode
        "vsetvli zero, %0, e32, m8, tu, mu\n"
        // load coefficients
        "vle32.v v16, 0(%4)\n"
        // perform permutation for coefficient
        "vrgatherei16.vv v8, v16, v4\n"
        // save coefficients
        "vse32.v v8, 0(%4)\n"

        // "n/2" mode
        "vsetvli zero, %1, e32, m4, tu, mu\n"
        // load twiddle factors
        "vle32.v v16, 0(%2)\n"
        // load half coefficients
        "vle32.v v8, 0(%4)\n"
        "vle32.v v12, 0(%5)\n"

    #ifdef DEBUG
        "vse32.v v8, 0(%6)\n"// c
        "vse32.v v12, 0(%7)\n"// c
        "vse32.v v16, 0(%8)\n"// c
    #endif

        // butterfly operation
        "vmul.vv v12, v12, v16\n"
        "vrem.vx v12, v12, %3\n"
        "vadd.vv v16, v8, v12\n" // TODO: will it overflow?
        "vsub.vv v20, v8, v12\n"
        // save half coefficients
        "vse32.v v16, 0(%4)\n"
        "vse32.v v20, 0(%5)\n"
        :
        : /* %0 */ "r"(n), 
          /* %1 */ "r"(n / 2),
          /* %2 */ "r"(twiddle + k * (n / 2)), 
          /* %3 */ "r"(p),
          "r"(dst),
          "r"(dst + (n / 2))
    #ifdef DEBUG
          , "r"(tmp1), "r"(tmp2), "r"(tmp3)
    #endif
    );
    #ifdef DEBUG
      for(int k = 0; k < USERN; k++) {
        printf("(%x, %x, %x)\n", tmp1[k], tmp2[k], tmp3[k]);
      }
    #endif
  }
  // deal with modular
  asm("vsetvli zero, %0, e32, m8, tu, mu\n"
      "vle32.v v16, 0(%1)\n"
      "vrem.vx v8, v16, %2\n"
      "vse32.v v8, 0(%1)\n"

      :
      : "r"(n), "r"(dst), "r"(p));
}
