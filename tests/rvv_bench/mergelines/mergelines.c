#include "bench.h"

size_t
mergelines_scalar(char *str, size_t len)
{
	char *dest = str;
	char *src = str;

	while (len > 1) {
		if (src[0] == '\\' && src[1] == '\n')
			src += 2, len -= 2;
		else
			*dest++ = *src++, --len;
		BENCH_CLOBBER();
	}
	if (len > 0)
		*dest++ = *src++;
	return dest - str;
}

#define IMPLS(f) \
	MX(f, rvv) \
	f(scalar) \
	MX(f, rvv_skip) \

typedef size_t Func(char *buf, size_t len);

#define DECLARE(f) extern Func mergelines_##f;
IMPLS(DECLARE)

#define EXTRACT(f) { #f, &mergelines_##f },
Impl impls[] = { IMPLS(EXTRACT) };

char *str;
ux last;

void init(void) { }
ux checksum(size_t n) { return last; }

void common(size_t n, char const *chars, size_t nChars) {
	str = (char*)mem + (urand() & 255);
	for (size_t i = 0; i < n; ++i)
		str[i] = chars[urand() % nChars];
}

BENCH(2_3) {
	common(n, "\\\na", 3);
	TIME last = (uintptr_t)f(str, n);
} BENCH_END

BENCH(2_16) {
	common(n, "\\\nabcdefgh", 16);
	TIME last = (uintptr_t)f(str, n);
} BENCH_END

BENCH(2_32) {
	common(n, "\\\nabcdefgh123456789", 32);
	TIME last = (uintptr_t)f(str, n);
} BENCH_END

BENCH(2_256) {
	str = (char*)mem + (urand() & 255);
	for (size_t i = 0; i < n; ++i)
		str[i] = urand() & 0xff;
	TIME last = (uintptr_t)f(str, n);
} BENCH_END

#define COUNT SCALE_mergelines(MAX_MEM) - 256
Bench benches[] = {
	{ COUNT, "mergelines 2/3", bench_2_3 },
	{ COUNT, "mergelines 2/16", bench_2_16 },
	{ COUNT, "mergelines 2/32", bench_2_32 },
	{ COUNT, "mergelines 2/256", bench_2_256 }
}; BENCH_MAIN(impls, benches)

