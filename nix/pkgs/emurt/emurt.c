#include <stdint.h>
#include <stdio.h>

struct uartlite_regs {
    volatile unsigned int rx_fifo;
    volatile unsigned int tx_fifo;
    volatile unsigned int status;
    volatile unsigned int control;
};

struct uartlite_regs *const ttyUL0 = (struct uartlite_regs *)0x60000000;

void uart_put_c(const char c) {
  while (ttyUL0->status & (1<<3) /* transmit FIFO full */);
  ttyUL0->tx_fifo = c;
}

void uart_put_s(const char *s) {
  while (*s) {
    uart_put_c(*s++);
  }
}

void uart_put_hex_d(uint64_t x) {
    for (int i=15;i>=0;i--) {
        uint64_t res = (x >> (i * 4)) & 0xf;
        uart_put_c(res >= 10 ? 'a' + res - 10 : '0' + res);
    }
}

extern char* heap_start;
char *heap_ptr;

char *_sbrk(int nbytes) {
  char *base;

  if (!heap_ptr)
    heap_ptr = (char *)&heap_start;
  base = heap_ptr;
  heap_ptr += nbytes;

  return base;
}

// We don't support FS
int _isatty(int file) {
  return -1;
}

// We don't support proc
int _kill(int pid, int sig) {
  return -1;
}

// We don't support proc
int _getpid() {
  return -1;
}

// We don't support FS
int _fstat(int file, struct stat* st) {
  return -1;
}

void _exit(int code) {
  __asm__("csrwi 0x7cc, 0");
  __builtin_unreachable();
}

// We don't support close
int _close(int file) {
  return -1;
}

// We don't support lseek
int _lseek(int file, int ptr, int dir) {
  return -1;
}

// TODO: We can support read
int _read(int file, char* ptr, int len) {
  return -1;
}

int _write(int file, char* ptr, int len) {
  int i = 0;
  for (; i < len; i++) {
    uart_put_c(ptr[i]);
  }
  return i;
}
