#include "trap.h"
#include <emurt.h>

void __attribute__((aligned(4))) trap_handler() {
    unsigned long mcause, mtval, mepc;
    asm volatile(
        "csrr %0, mcause"
        : "=r" (mcause)
    );
    asm volatile(
        "csrr %0, mtval"
        : "=r" (mtval)
    );
    asm volatile(
        "csrr %0, mepc"
        : "=r" (mepc)
    );
    print_s("Exception: ");
    print_s("\nmcause: ");
    dump_hex(mcause);
    print_s("mtval: ");
    dump_hex(mtval);
    print_s("mepc: ");
    dump_hex(mepc);
    while(1);
}

void setup_mtvec() {
    void* ptr = &trap_handler;
    print_s("setting mtvec to ");
    dump_hex((unsigned long)ptr);
    print_s("\n");
    if (((unsigned long)ptr & 0b11)) {
        print_s("Error! mtvec does not aligned to 4B\n");
    }
    asm volatile(
        "csrw mtvec, %0"
        :
        : "r" (ptr)
    );
}

void enter_smode() {
    asm volatile("csrc mstatus, %0" : : "r" (0x1800)); // clear mpp to zero
    asm volatile("csrs mstatus, %0" : : "r" (0x0800)); // set mpp to s-mode
    asm volatile(
        ".option arch, -c\n"
        "auipc a0, 0\n"
        "addi a0, a0, 16\n"
        "csrw mepc, a0\n"
        "mret\n"
        :
        :
        : "a0"
    );
}
