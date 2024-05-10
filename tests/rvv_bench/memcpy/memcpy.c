#include "bench.h"

void *
memcpy_scalar(void *restrict dest, void const *restrict src, size_t n)
{
	unsigned char *d = dest;
	unsigned char const *s = src;
	while (n--) *d++ = *s++, BENCH_CLOBBER();
	return dest;
}

void *
memcpy_scalar_autovec(void *restrict dest, void const *restrict src, size_t n)
{
	unsigned char *d = dest;
	unsigned char const *s = src;
	while (n--) *d++ = *s++;
	return dest;
}

/* https://git.musl-libc.org/cgit/musl/tree/src/string/memcpy.c */
void *
memcpy_musl(void *restrict dest, void const *restrict src, size_t n)
{
	unsigned char *d = dest;
	unsigned char const *s = src;

#ifdef __GNUC__

#if __BYTE_ORDER == __LITTLE_ENDIAN
#define LS >>
#define RS <<
#else
#define LS <<
#define RS >>
#endif

	typedef uint32_t __attribute__((__may_alias__)) u32;
	uint32_t w, x;

	for (; (uintptr_t)s % 4 && n; n--) *d++ = *s++;

	if ((uintptr_t)d % 4 == 0) {
		for (; n>=16; s+=16, d+=16, n-=16) {
			*(u32 *)(d+0) = *(u32 *)(s+0);
			*(u32 *)(d+4) = *(u32 *)(s+4);
			*(u32 *)(d+8) = *(u32 *)(s+8);
			*(u32 *)(d+12) = *(u32 *)(s+12);
		}
		if (n&8) {
			*(u32 *)(d+0) = *(u32 *)(s+0);
			*(u32 *)(d+4) = *(u32 *)(s+4);
			d += 8; s += 8;
		}
		if (n&4) {
			*(u32 *)(d+0) = *(u32 *)(s+0);
			d += 4; s += 4;
		}
		if (n&2) {
			*d++ = *s++; *d++ = *s++;
		}
		if (n&1) {
			*d = *s;
		}
		return dest;
	}

	if (n >= 32) switch ((uintptr_t)d % 4) {
	case 1:
		w = *(u32 *)s;
		*d++ = *s++;
		*d++ = *s++;
		*d++ = *s++;
		n -= 3;
		for (; n>=17; s+=16, d+=16, n-=16) {
			x = *(u32 *)(s+1);
			*(u32 *)(d+0) = (w LS 24) | (x RS 8);
			w = *(u32 *)(s+5);
			*(u32 *)(d+4) = (x LS 24) | (w RS 8);
			x = *(u32 *)(s+9);
			*(u32 *)(d+8) = (w LS 24) | (x RS 8);
			w = *(u32 *)(s+13);
			*(u32 *)(d+12) = (x LS 24) | (w RS 8);
		}
		break;
	case 2:
		w = *(u32 *)s;
		*d++ = *s++;
		*d++ = *s++;
		n -= 2;
		for (; n>=18; s+=16, d+=16, n-=16) {
			x = *(u32 *)(s+2);
			*(u32 *)(d+0) = (w LS 16) | (x RS 16);
			w = *(u32 *)(s+6);
			*(u32 *)(d+4) = (x LS 16) | (w RS 16);
			x = *(u32 *)(s+10);
			*(u32 *)(d+8) = (w LS 16) | (x RS 16);
			w = *(u32 *)(s+14);
			*(u32 *)(d+12) = (x LS 16) | (w RS 16);
		}
		break;
	case 3:
		w = *(u32 *)s;
		*d++ = *s++;
		n -= 1;
		for (; n>=19; s+=16, d+=16, n-=16) {
			x = *(u32 *)(s+3);
			*(u32 *)(d+0) = (w LS 8) | (x RS 24);
			w = *(u32 *)(s+7);
			*(u32 *)(d+4) = (x LS 8) | (w RS 24);
			x = *(u32 *)(s+11);
			*(u32 *)(d+8) = (w LS 8) | (x RS 24);
			w = *(u32 *)(s+15);
			*(u32 *)(d+12) = (x LS 8) | (w RS 24);
		}
		break;
	}
	if (n&16) {
		*d++ = *s++; *d++ = *s++; *d++ = *s++; *d++ = *s++;
		*d++ = *s++; *d++ = *s++; *d++ = *s++; *d++ = *s++;
		*d++ = *s++; *d++ = *s++; *d++ = *s++; *d++ = *s++;
		*d++ = *s++; *d++ = *s++; *d++ = *s++; *d++ = *s++;
	}
	if (n&8) {
		*d++ = *s++; *d++ = *s++; *d++ = *s++; *d++ = *s++;
		*d++ = *s++; *d++ = *s++; *d++ = *s++; *d++ = *s++;
	}
	if (n&4) {
		*d++ = *s++; *d++ = *s++; *d++ = *s++; *d++ = *s++;
	}
	if (n&2) {
		*d++ = *s++; *d++ = *s++;
	}
	if (n&1) {
		*d = *s;
	}
	return dest;
#endif

	while (n--) { *d++ = *s++; BENCH_CLOBBER(); }
	return dest;
}

#define memcpy_libc memcpy

#define IMPLS(f) \
	IFHOSTED(f(libc)) \
	f(musl) \
	f(scalar) \
	f(scalar_autovec) \
	MX(f, rvv) \
	MX(f, rvv_align_dest) \
	MX(f, rvv_align_src) \
	MX(f, rvv_align_dest_hybrid) \
	MX(f, rvv_tail) \
	MX(f, rvv_128) \

typedef void *Func(void *restrict dest, void const *restrict src, size_t n);

#define DECLARE(f) extern Func memcpy_##f;
IMPLS(DECLARE)

#define EXTRACT(f) { #f, &memcpy_##f },
Impl impls[] = { IMPLS(EXTRACT) };

uint8_t *dest, *src;
ux last;

void init(void) { }

ux checksum(size_t n) {
	ux sum = last;
	for (size_t i = 0; i < n+9; ++i)
		sum = uhash(sum) + dest[i];
	return sum;
}

void common(size_t n, size_t dOff, size_t sOff) {
	dest = mem + dOff; src = dest + MAX_MEM/2 + sOff + 9;
	memset(dest, 0, n+9);
}

BENCH(base) {
	common(n, urand() & 255, urand() & 255);
	TIME last = (uintptr_t)f(dest, src, n);
} BENCH_END

BENCH(aligned) {
	common(n, 0, 0);
	TIME last = (uintptr_t)f(dest, src, n);
} BENCH_END

Bench benches[] = {
	{ MAX_MEM/2 - 521, "memcpy", bench_base },
	{ MAX_MEM/2 - 521, "memcpy aligned", bench_aligned}
}; BENCH_MAIN(impls, benches)

