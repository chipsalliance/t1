#include <emurt.h>
#include <stdio.h>

#include "trap.h"

void __attribute__((aligned(4))) trap_handler() {
    unsigned long mcause, mtval, mepc;
    asm volatile(
        "csrr %0, mcause\n"
        "csrr %1, mtval\n"
        "csrr %2, mepc\n"
        : "=r" (mcause), "=r" (mtval), "=r" (mepc)
    );
    printf("Exception: mcause=%08lx, mtval=%08lx, mepc=%08lx", mcause, mtval, mepc);
    while(1);
}

void setup_mtvec() {
    void* ptr = &trap_handler;
    asm volatile(
        "csrw mtvec, %0"
        :
        : "r" (ptr)
    );
}

void enter_smode() {
    asm volatile(
        "csrc mstatus, %0\n"
        "csrs mstatus, %1\n"
        ".option arch, -c\n"
        "auipc %2, 0\n"
        "addi %2, %2, 16\n"
        "csrw mepc, %2\n"
        "mret\n"
        :
        : "r" (0x1800), "r" (0x0800), "r" (0x10)
    );
}
