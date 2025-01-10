#include "emurt.h"

#define EXIT_REG 0x10000000
#define UART_W_REG 0x10000010
#define PERF_REG 0x10000014

void t1_put_char(char c) { *(uint32_t volatile *)(UART_W_REG) = (uint8_t)c; }
void place_counter(int i) { *(int volatile *)(PERF_REG) = i; }

///////////////////////
// uart
///////////////////////
int _write(int file, char *ptr, int len) {
  int i = 0;
  for (; i < len; i++) {
    t1_put_char(ptr[i]);
  }
  return i;
}

void _exit(int code) {
  __asm__("li x1, 0x10000000");
  __asm__("li x2, 0xdeadbeef");
  __asm__("sw x2, 0(x1)");
  __asm__("j .");
  __builtin_unreachable();
}

void print_s(const char *c) {
  while (*c) {
    t1_put_char(*c);
    c++;
  }
}

///////////////////////
// allocation
///////////////////////

extern char *__heapbegin;
char *heap_top;

char *_sbrk(int nbytes) {
  char *base;

  if (!heap_top)
    heap_top = (char *)&__heapbegin;
  base = heap_top;
  heap_top += nbytes;

  return base;
}

///////////////////////
// unimplemented
///////////////////////

// We don't support FS
int _isatty(int file) { return -1; }

// We don't support proc
int _kill(int pid, int sig) { return -1; }

// We don't support proc
int _getpid() { return -1; }

// We don't support FS
int _fstat(int file, struct stat *st) { return -1; }

// We don't support close
int _close(int file) { return -1; }

// We don't support lseek
int _lseek(int file, int ptr, int dir) { return -1; }

// TODO: We can support read
int _read(int file, char *ptr, int len) { return -1; }
