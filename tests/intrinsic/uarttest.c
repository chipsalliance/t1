#include <emurt.h>

void test() {
    uart_put_s("Test Begin from UART!\n");
    uart_put_hex_d(0xdeadbeef);
    uart_put_c('\n');
    uart_put_s("Test End from UART!\n");
}
