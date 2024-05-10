#include "bench.h"

size_t
utf8_count_scalar(char const *str, size_t len)
{
	uint8_t const *p = (uint8_t const*)str;
	size_t count = 0;
	while (len--) count += (*p++ & 0xc0) != 0x80, BENCH_CLOBBER();
	return count;
}

size_t
utf8_count_scalar_autovec(char const *str, size_t len)
{
	uint8_t const *p = (uint8_t const*)str;
	size_t count = 0;
	while (len--) count += (*p++ & 0xc0) != 0x80;
	return count;
}

#define GEN_SWAR(name, popc, clobber)  \
	size_t \
	utf8_count_##name(char const *str, size_t len) \
	{ \
		ux const BENCH_MAY_ALIAS *u; \
		size_t count = 0, tail = 0; \
\
		uint8_t const *u8 = (uint8_t const*)str; \
		if (len < sizeof *u) { \
			tail = len; \
			goto skip; \
		} \
\
		tail = sizeof *u - (uintptr_t)str % sizeof *u; \
\
		len -= tail; \
		while (tail--) \
			count += (*u8++ & 0xC0) != 0x80, clobber; \
\
		u = (ux const*)u8; \
		tail = len % sizeof *u; \
\
		for (len /= sizeof *u; len--; ++u) { \
			ux b1 =  ~*u & (ux)0x8080808080808080; \
			ux b2 =  *u & (ux)0x4040404040404040; \
			count += popc((b1 >> 1) | b2); \
			clobber; \
		} \
\
		u8 = (uint8_t const*)u; \
	skip: \
		while (tail--) \
			count += (*u8++ & 0xC0) != 0x80, clobber; \
		return count; \
	}

#if __riscv_zbb
GEN_SWAR(SWAR_popc,__builtin_popcountll,BENCH_CLOBBER())
GEN_SWAR(SWAR_popc_autovec,__builtin_popcountll,(void)0)
# define POPC(f) f(SWAR_popc) f(SWAR_popc_autovec)
#else
# define POPC(f)
#endif

static inline int
upopcnt(ux x)
{
	/* 2-bit sums */
	x -= (x >> 1) & (-(ux)1/3);
	/* 4-bit sums */
	x = (x & (-(ux)1/15*3)) + ((x >> 2) & (-(ux)1/15*3));
	/* 8-bit sums */
	x = (x + (x >> 4)) & (-(ux)1/255*15);
	BENCH_VOLATILE_REG(x);
	/* now we can just add the sums together, because can't overflow,
	 * since there can't be more than 255 bits set */
	x += (x >>  8); /* 16-bit sums */
	x += (x >> 16); /* sum 16-bit sums */
	IF64(x += (x >> 32)); /* sum 32-bit sums */
	return x & 127;
}


GEN_SWAR(SWAR_popc_bithack,upopcnt,BENCH_CLOBBER())
GEN_SWAR(SWAR_popc_bithack_autovec,upopcnt,(void)0)


#define IMPLS(f) \
	MX(f, rvv) \
	f(scalar) \
	f(scalar_autovec) \
	POPC(f) \
	f(SWAR_popc_bithack) \
	f(SWAR_popc_bithack_autovec) \
	MX(f, rvv_align) \
	MX(f, rvv_tail) \
	MX(f, rvv_128) \
	MX(f, rvv_4x) \
	MX(f, rvv_4x_tail) \

typedef size_t Func(char const *str, size_t len);

#define DECLARE(f) extern Func utf8_count_##f;
IMPLS(DECLARE)

#define EXTRACT(f) { #f, &utf8_count_##f },
Impl impls[] = { IMPLS(EXTRACT) };

char *str;
ux last;

void init(void) { }
ux checksum(size_t n) { return last; }

void common(size_t n, size_t off) {
	str = (char*)mem + off;
	memrand(str, n + 9);
}

BENCH(base) {
	common(n, urand() & 511);
	TIME last = (uintptr_t)f(str, n);
} BENCH_END

BENCH(aligned) {
	common(n, 0);
	TIME last = (uintptr_t)f(str, n);
} BENCH_END

Bench benches[] = {
	{ MAX_MEM - 521, "utf8 count", bench_base },
	{ MAX_MEM - 521, "utf8 count aligned", bench_aligned }
}; BENCH_MAIN(impls, benches)


