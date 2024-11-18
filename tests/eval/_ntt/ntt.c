#include <assert.h>
#include <stdio.h>

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
  assert(elements_in_vreg >= n);

  asm("vsetvli zero, %0, e16, m4, tu, mu\n"
      "vid.v v4\n"
      :
      : "r"(n));

  // prepare the permutation list
  for (int k = 0; 2 * k <= l; k++) {
    asm("vand.vx v8, v4, %0\n"
        "vsub.vv v4, v4, v8\n"
        "vsll.vx v8, v8, %1\n" // get the k-th digit and shift left

        "vand.vx v12, v4, %2\n"
        "vsub.vv v4, v4, v12\n"
        "vsrl.vx v12, v12, %1\n" // get the (l-k)-th digit and shift right

        "vor.vv v4, v4, v8\n"
        "vor.vv v4, v4, v12\n"
        :
        : "r"(1 << k), "r"(l - 1 - 2 * k), "r"(1 << (l - k)));
  }

  asm("vsetvli zero, %0, e32, m8, tu, mu\n"
      "vle32.v v16, 0(%1)\n"
      "vrgatherei16.vv v8, v16, v4\n"

      // set v16 to all 1
      "vxor.vv v16, v16, v16\n"
      "vadd.vi v16, v16, 1\n"
      :
      : "r"(n), "r"(array));

  for (int k = 0; k < l; k++) {
    asm(                               // prepare coefficients in v16-23
        "vid.v v24\n"                  // v24-31[i] = i
        "vand.vx v24, v24, %1\n"       // v24-31[i] = i & (1 << k)
        "vmsne.vi v0, v24, 0\n"        // vm0[i] = i & (1 << k) != 0
        "vmul.vx v16, v16, %2, v0.t\n" // v16-23[i] = w^(???)

        // prepare shifted elements in v24-31
        "vslideup.vx v24, v8, %3\n" // shift the first 2^(l-k) elements to tail
        "vsetvli zero, %3, e32, m8, tu, mu\n" // last n - 2^(l-k) elements
        "vslidedown.vx v24, v8, %4\n"

        // mul and add
        "vsetvli zero, %0, e32, m8, tu, mu\n"
        "vmul.vv v24, v24, v16\n"
        "vrem.vx v24, v24, %5\n"
        "vadd.vv v8, v8, v24\n" // TODO: will it overflow?
        :
        : "r"(n), /* %1 */ "r"(1 << k), /* %2 */ "r"(twindle[l - 1 - k]),
          /* %3 */ "r"(n - (1 << (l - k))),
          /* %4 */ "r"(1 << (l - k)), /* %5 */ "r"(p));
  }
  asm("vse32.v v8, 0(%0)\n" : : "r"(dst));
}

