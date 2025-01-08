#pragma once

#include <stdint.h>

struct uartlite_regs {
  volatile unsigned int rx_fifo;
  volatile unsigned int tx_fifo;
  volatile unsigned int status;
  volatile unsigned int control;
};

void get(char *s, int n);
void print_s(const char *c);
