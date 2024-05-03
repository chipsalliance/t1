#pragma once

#include <stdint.h>

struct uartlite_regs {
    volatile unsigned int rx_fifo;
    volatile unsigned int tx_fifo;
    volatile unsigned int status;
    volatile unsigned int control;
};

void uart_put_c(char c);
char uart_check_read();
char uart_get_c();
void get(char *s, int n);
void print_s(const char *c);
void print_long(long x);
void print_digit(unsigned char x);
void dump_hex(unsigned long x);

