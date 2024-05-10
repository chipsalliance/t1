/* processor specific configs */
#define HAS_E64 (__riscv_v_elen >= 64)
#define HAS_F16 0

/* the maximum number of bytes to allocate, minimum of 4096 */
#define MAX_MEM (4096 * 8)
/* the byte count for the next run */
#define NEXT(c) (c + c / 3 + 3)

/* minimum number of repeats, to sample median from */
#define MIN_REPEATS 1
/* maxium number of repeats, executed until more than STOP_TIME has elapsed */
#define MAX_REPEATS 1

/* stop repeats early afer this many cycles have elapsed */
#define STOP_CYCLES (1024 * 1024 * 500)

/* custom scaling factors for benchmarks, these are used to make sure each
 * benchmark approximately takes the same amount of time. */

#define SCALE_mandelbrot(N) ((N) / 10)
#define SCALE_mergelines(N) ((N) / 10)

/* benchmark specific configurations */
#define mandelbrot_ITER 100
