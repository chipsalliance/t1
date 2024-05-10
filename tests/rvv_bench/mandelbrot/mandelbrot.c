#include "bench.h"

void
mandelbrot_scalar_f32(size_t width, size_t maxIter, uint32_t *res)
{
	for (size_t y = 0; y < width; ++y)
	for (size_t x = 0; x < width; ++x) {
		float cx = x * 2.0f / width - 1.5;
		float cy = y * 2.0f / width - 1;
		size_t iter = 0;
		float zx = 0, zy = 0, zxS = 0, zyS = 0;

		BENCH_VOLATILE_REG(cy);
		while (zxS + zyS <= 4 && iter < maxIter) {
			zxS = zxS - zyS + cx;
			zy = 2 * zx * zy + cy;
			zx = zxS;
			BENCH_VOLATILE_REG(zx);
			zxS = zx*zx;
			zyS = zy*zy;
			++iter;
			BENCH_CLOBBER();
		}
		*res++ = iter;
	}
}

#if __riscv_xlen >= 64
void
mandelbrot_scalar_f64(size_t width, size_t maxIter, uint32_t *res)
{
	for (size_t y = 0; y < width; ++y)
	for (size_t x = 0; x < width; ++x) {
		double cx = x * 2.0 / width - 1.5;
		double cy = y * 2.0 / width - 1;
		size_t iter = 0;
		double zx = 0, zy = 0, zxS = 0, zyS = 0;

		BENCH_VOLATILE_REG(cy);
		while (zxS + zyS <= 4 && iter < maxIter) {
			zxS = zxS - zyS + cx;
			zy = 2 * zx * zy + cy;
			zx = zxS;
			BENCH_VOLATILE_REG(zx);
			zxS = zx*zx;
			zyS = zy*zy;
			++iter;
		}
		*res++ = iter;
	}
}
#endif

#if HAS_F16
# define IMPLS_F16(f) f(rvv_f16_m1) f(rvv_f16_m2)
#else
# define IMPLS_F16(f)
#endif

#define IMPLS(f) \
	f(rvv_f32_m1) \
	f(scalar_f32) \
	IF64(f(scalar_f64)) \
	IMPLS_F16(f) \
	f(rvv_f32_m2) \
	IF64(f(rvv_f64_m2)) \

typedef void Func(size_t width, size_t maxIter, uint32_t *res);

#define DECLARE(f) extern Func mandelbrot_##f;
IMPLS(DECLARE)

#define EXTRACT(f) { #f, &mandelbrot_##f },
Impl impls[] = { IMPLS(EXTRACT) };

uint32_t *dest;
void init(void) { memset(mem, 0, MAX_MEM); dest = (uint32_t*)mem; }

/* disabled, because of rounding errors, please independently verify */
ux checksum(size_t n) { return 0; }

BENCH(base) {
	n = usqrt(n);
	TIME f(n, mandelbrot_ITER, dest);
} BENCH_END

Bench benches[] = {
	{
		SCALE_mandelbrot(MAX_MEM / 4),
		"mandelbrot "STR(mandelbrot_ITER),
		bench_base
	},
}; BENCH_MAIN(impls, benches)

