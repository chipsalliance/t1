#include "bench.h"

void
ascii_to_utf32_scalar(uint32_t *restrict dest, uint8_t const *restrict src, size_t len)
{
	while (len--) *dest++ = *src++, BENCH_CLOBBER();
}

void
ascii_to_utf32_scalar_autovec(uint32_t *restrict dest, uint8_t const *restrict src, size_t len)
{
	while (len--) *dest++ = *src++;
}

#define IMPLS(f) \
	f(scalar) f(scalar_autovec) \
	f(rvv_ext_m1) f(rvv_ext_m2) \
	f(rvv_vsseg_m1) f(rvv_vsseg_m2) \
	f(rvv_vss_m1) f(rvv_vss_m2) \

typedef void Func(uint32_t *restrict dest, uint8_t const *restrict src, size_t len);

#define DECLARE(f) extern Func ascii_to_utf32_##f;
IMPLS(DECLARE)

#define EXTRACT(f) { #f, &ascii_to_utf32_##f },
Impl impls[] = { IMPLS(EXTRACT) };

uint32_t *dest;
uint8_t *src;

void init(void) { }

ux checksum(size_t n) {
	ux sum = 0;
	for (size_t i = 0; i < n+9; ++i)
		sum = uhash(sum) + dest[i];
	return sum;
}

void common(size_t n, size_t dOff, size_t sOff) {
	dest = (uint32_t*)mem + dOff/4;
	src = (uint8_t*)(dest + 9 + MAX_MEM/5) + sOff;
	memrand(src, n+9);
	for (size_t i = 0; i < n+9; ++i) src[i] |= 0x7F;
	memset(dest, 1, (n+9)*4);
}

BENCH(base) {
	common(n, urand() & 255, urand() & 255);
	TIME f(dest, src, n);
} BENCH_END

BENCH(aligned) {
	common(n, 0, 0);
	TIME f(dest, src, n);
} BENCH_END

Bench benches[] = {
	{ MAX_MEM/5 - 512-9*2, "ascii to utf32", bench_base },
	{ MAX_MEM/5 - 512-9*2, "ascii to utf32 aligned", bench_aligned },
}; BENCH_MAIN(impls, benches)

