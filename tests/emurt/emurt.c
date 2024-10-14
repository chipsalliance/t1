#include "emurt.h"

///////////////////////
// uart
///////////////////////

struct uartlite_regs *const ttyUL0 = (struct uartlite_regs *)0x10000000;

#define SR_TX_FIFO_FULL         (1<<3) /* transmit FIFO full */
#define SR_TX_FIFO_EMPTY        (1<<2) /* transmit FIFO empty */
#define SR_RX_FIFO_VALID_DATA   (1<<0) /* data in receive FIFO */
#define SR_RX_FIFO_FULL         (1<<1) /* receive FIFO full */

#define ULITE_CONTROL_RST_TX    0x01
#define ULITE_CONTROL_RST_RX    0x02

void uart_put_c(char c) {
    while (ttyUL0->status & SR_TX_FIFO_FULL);
    ttyUL0->tx_fifo = c;
}

char uart_check_read() { // 1: data ready, 0: no data
    return (ttyUL0->status & SR_RX_FIFO_VALID_DATA) != 0;
}

char uart_get_c() {
    return ttyUL0->rx_fifo;
}

void get(char *s, int n) {
    char debug[20] = "Enter get()";
    print_s(debug);
    int i = 0;
    while (uart_check_read() != 1);
    while (uart_check_read() && i < n - 1) {
        print_s(debug);
        char c = uart_get_c();
        uart_put_c(c);
        if (c == '\r' || c == '\n') {
            break; // Break if carriage return or newline is encountered
        }
        *(s + i) = c;
        i++;
    }
    s[i] = '\0';
}

int _write(int file, char* ptr, int len) {
  int i = 0;
  for (; i < len; i++) {
    uart_put_c(ptr[i]);
  }
  return i;
}

void _exit(int code) {
  __asm__("li x1, 0x40000000");
  __asm__("li x2, 0xdeadbeef");
  __asm__("sw x2, 0(x1)");
  __asm__("j .");
  __builtin_unreachable();
}

void print_s(const char *c) {
    while (*c) {
        uart_put_c(*c);
        c ++;
    }
}

///////////////////////
// allocation
///////////////////////

extern char* __heapbegin;
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

