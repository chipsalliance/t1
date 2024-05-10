#include "bench.h"
#if __riscv_xlen >= 64
#include "thirdparty/boring.h"

uint8_t *src;
uint8_t key[32], sig[16];

extern uint64_t
vector_poly1305(const uint8_t* in, size_t len,
                const uint8_t key[32], uint8_t sig[16]);

static void
poly1305_boring(void const *src, size_t n) {
	poly1305_state state;
	boring_poly1305_init(&state, key);
	boring_poly1305_update(&state, src, n);
	boring_poly1305_finish(&state, sig);
}

static void
poly1305_rvv(void const *src, size_t n) {
	vector_poly1305(src, n, key, sig);
}

typedef void *Func(void const *src, size_t n);

Impl impls[] = {
	{ "boring", &poly1305_boring },
#if HAS_E64
	{ "rvv", &poly1305_rvv },
#endif
};

void init(void) {
	memrand(key, sizeof key);
	memrand(sig, sizeof sig);
}

ux checksum(size_t n) {
	ux sum = 0;
	for (size_t i = 0; i < ARR_LEN(sig); ++i)
		sum = uhash(sum) + sig[i];
	return sum;
}

BENCH(aligned) {
	for (size_t i = 0; i < 256; ++i)
		mem[urand()%n] = urand();
	n = (15+n) & -16;
	TIME f(mem, n);
} BENCH_END

Bench benches[] = {
	{ MAX_MEM, "poly1305 aligned", bench_aligned }
}; BENCH_MAIN(impls, benches)


#include "../thirdparty/boring.c"
#else
void init(void) {}
Impl impls[] = {};
Bench benches[] = {};
BENCH_MAIN(impls, benches)
#endif
