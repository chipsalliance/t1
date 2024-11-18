#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

// array is of length n=2^l, p is a prime number
// roots is of length l, where g = roots[0] satisfies that
//   g^(2^l) == 1 mod p  and g^(2^(l-1)) == -1 mod p
// roots[i] = g^(2^i)  (hence roots[l - 1] = -1)
//
// 32bit * n <= VLEN * 8 => n <= VLEN / 4
void ntt(const int *array, int l, const int *twindle, int p, int *dst) {
  // prepare an array of permutation indices
  assert(l <= 16);

  int n = 1 << l;
  int g = twindle[0];

  // registers:
  // v8-15: array
  // v16-24: loaded elements (until vrgather)
  // v4-7: permutation index (until vrgather)
  // v16-24: coefficients
  int vlenb;
  asm("csrr %0, vlenb" : "=r"(vlenb));
  int elements_in_vreg = vlenb * 2;

  int log2_elements_in_vreg = 0;
  for (int i = 1; i < elements_in_vreg; i *= 2) {
    log2_elements_in_vreg += 1;
  }

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

  // we will store coefficients in v16-23, first init to all 1
  asm("vsetvli zero, %0, e32, m8, tu, mu\n"
      // set v16 to all 1
      "vxor.vv v16, v16, v16\n"
      "vadd.vi v16, v16, 1\n"
      :
      : "r"(n));

  // buf is used to store shifted elements
  int *buf = (int *)malloc(elements_in_vreg * sizeof(int));

  for (int k = 0; k < l; k++) {
    int offset = 1 << (l - k);
    // perform dst[i] = dst[i] + g^(offset * i) * dst[i + offset]

    // prepare offseted array, i.e. buf[i] = dst[i + offset]
    for (int base = 0; base < n; base += elements_in_vreg) {
      // handle the border
      if (base == 0 && offset < elements_in_vreg) {
        asm("vsetvli zero, %0, e32, m8, tu, mu\n"
            "vle32.v v8, 0(%1)\n"
            "vse32.v v8, 0(%2)\n"
            :
            : /* %0 */ "r"(offset), /* %1 */ "r"(dst),
              /* %2 */ "r"(buf + n - offset)
            : "memory");
        asm("vsetvli zero, %0, e32, m8, tu, mu\n"
            "vle32.v v8, 0(%1)\n"
            "vse32.v v8, 0(%2)\n"
            :
            : /* %0 */ "r"(elements_in_vreg - offset),
              /* %1 */ "r"(dst + offset),
              /* %2 */ "r"(buf)
            : "memory");
      } else {
        asm("vsetvli zero, %0, e32, m8, tu, mu\n"
            "vle32.v v8, 0(%1)\n"
            "vse32.v v8, 0(%2)\n"
            :
            : /* %0 */ "r"(elements_in_vreg), /* %1 */ "r"(dst + base),
              /* %2 */ "r"(buf + ((base + offset) % n))
            : "memory");
      }
    }

    // prepare coefficients
    int multiplier = twindle[l - 1 - k];
    asm( // prepare coefficients in v16-23
        "vsetvli zero, %0, e32, m8, tu, mu\n"
        "vid.v v24\n"                  // v24-31[i] = i
        "vand.vx v24, v24, %1\n"       // v24-31[i] = i & (1 << k)
        "vmsne.vi v0, v24, 0\n"        // vm0[i] = i & (1 << k) != 0
        "vmul.vx v16, v16, %2, v0.t\n" // v16-23[i] = w^(???)
        :
        : /* %0 */ "r"(n), /* %1 */ "r"(1 << k), /* %2 */ "r"(multiplier));

    for (int base = 0; base < n; base += elements_in_vreg) {
      asm("vle32.v v8, 0(%0)\n"
          "vle32.v v16, 0(%1)\n"

          // mul and add
          "vmul.vv v24, v24, v16\n"
          "vrem.vx v24, v24, %2\n"
          "vadd.vv v8, v8, v24\n" // TODO: will it overflow?
          "vse32.v v8, 0(%3)"
          :
          : /* %0 */ "r"(dst + offset), /* %1 */ "r"(buf + offset),
            /* %2 */ "r"(p),
            /* %3 */ "r"(dst + offset)
          : "memory");

      // when step * elements_in_vreg <= n, i.e. (1 << k) >= elements_in_vreg
      // shift the coefficients
      if (k >= log2_elements_in_vreg) {
        int step_multiplier = twindle[l - 1 - k + log2_elements_in_vreg];
        asm("vmul.vx v16, v16, %0" : : "r"(step_multiplier));
      }
    }
  }

  free(buf);
}
