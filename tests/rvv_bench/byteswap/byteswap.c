#include "bench.h"

void
byteswap32_scalar(uint32_t *ptr, size_t n)
{
	for (uint8_t *p = (uint8_t*)ptr; n--; p += 4) {
		uint8_t p0 = p[0], p1 = p[1], p2 = p[2], p3 = p[3];
		p[3] = p0; BENCH_CLOBBER();
		p[2] = p1; BENCH_CLOBBER();
		p[1] = p2; BENCH_CLOBBER();
		p[0] = p3; BENCH_CLOBBER();
	}
}

void
byteswap32_scalar_autovec(uint32_t *ptr, size_t n)
{
	for (uint8_t *p = (uint8_t*)ptr; n--; p += 4) {
		uint8_t p0 = p[0], p1 = p[1], p2 = p[2], p3 = p[3];
		p[3] = p0;
		p[2] = p1;
		p[1] = p2;
		p[0] = p3;
	}
}

#if __riscv_zbb
void
byteswap32_SWAR_rev8(uint32_t *ptr, size_t n)
{
	while (n--) {
		*ptr = __builtin_bswap32(*ptr);
		++ptr;
		BENCH_CLOBBER();
	}
}
#define REV8(f) f(SWAR_rev8)
#else
#define REV8(f)
#endif


#define IMPLS(f) \
	f(scalar) \
	f(scalar_autovec) \
	REV8(f) \
	MX(f, rvv_gather) \
	f(rvv_m1_gathers_m2) \
	f(rvv_m1_gathers_m4) \
	f(rvv_m1_gathers_m8) \

typedef void Func(uint32_t *ptr, size_t n);

#define DECLARE(f) extern Func byteswap32_##f;
IMPLS(DECLARE)

#define EXTRACT(f) { #f, &byteswap32_##f },
Impl impls[] = { IMPLS(EXTRACT) };

uint32_t *ptr;

void init(void) { ptr = (uint32_t*)mem; }

ux checksum(size_t n) {
	ux sum = 0;
	for (size_t i = 0; i < n; ++i)
		sum = uhash(sum) + ptr[i];
	return sum;
}

BENCH(base) {
	memrand(ptr, n * sizeof *ptr);
	TIME f(ptr, n);
} BENCH_END

Bench benches[] = {
	{ MAX_MEM/4, "byteswap32", bench_base }
}; BENCH_MAIN(impls, benches)

