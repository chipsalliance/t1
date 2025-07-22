    .globl _start

// tohost address define in ld
_poweroff_spike:
    // override a0. Spike have special treatment for value at tohost address.
    li a0, 1
    lui a5,%hi(tohost)
    sw a0,%lo(tohost)(a5)
    ebreak
    ret

_poweroff_pokedex:
    lui a5,%hi(pokedex_mmio_exit)
    sw a0,%lo(pokedex_mmio_exit)(a5)
    ret


_trap_handler:
    // exit with 4, common used for SIGILL
    li a0, 4
    call _poweroff_pokedex
    call _poweroff_spike
    j .


_poweroff:
    // exit with 0
    li a0, 0
    call _poweroff_pokedex
    call _poweroff_spike
    j .


_start:
    li a0, 0x2200 # MSTATUS_VS & (MSTATUS_VS >> 1)
    csrs mstatus, a0
    csrwi vcsr, 0
    csrr a0, mhartid
    li x1, 0
    li x2, 0
    li x3, 0
    li x4, 0
    li x5, 0
    li x6, 0
    li x7, 0
    li x8, 0
    li x9, 0
    li x10, 0
    li x11, 0
    li x12, 0
    li x13, 0
    li x14, 0
    li x15, 0
    li x16, 0
    li x17, 0
    li x18, 0
    li x19, 0
    li x20, 0
    li x21, 0
    li x22, 0
    li x23, 0
    li x24, 0
    li x25, 0
    li x26, 0
    li x27, 0
    li x28, 0
    li x29, 0
    li x30, 0
    li x31, 0

    la sp, __stacktop

    la t0, _trap_handler
    csrw mtvec, t0

    // no ra to save
    call test

    // exit
    call _poweroff

    .p2align 2

