#pragma once

#include <stdint.h>

struct uartlite_regs {
    unsigned int rx_fifo;
    unsigned int tx_fifo;
    unsigned int status;
    unsigned int control;
};

volatile struct uartlite_regs *const ttyUL0 = (struct uartlite_regs *)0x10000000;

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
