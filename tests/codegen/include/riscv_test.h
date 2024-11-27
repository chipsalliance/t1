#ifndef _ENV_PHYSICAL_SINGLE_CORE_H
#define _ENV_PHYSICAL_SINGLE_CORE_H

#include "encoding.h"

//-----------------------------------------------------------------------
// Begin Macro
//-----------------------------------------------------------------------

#define RVTEST_RV64U                                                    \
  .macro init;                                                          \
  .endm

#define RVTEST_RV64UF                                                   \
  .macro init;                                                          \
  RVTEST_FP_ENABLE;                                                     \
  .endm

#define RVTEST_RV64UV                                                          \
  .macro init;                                                                 \
  RVTEST_VECTOR_ENABLE;                                                        \
  .endm

#define RVTEST_RV64UFV                                                  \
  .macro init;                                                          \
  RVTEST_FP_VECTOR_ENABLE;                                              \
  .endm

#define RVTEST_RV32U                                                    \
  .macro init;                                                          \
  .endm

#define RVTEST_RV32UF                                                   \
  .macro init;                                                          \
  RVTEST_FP_ENABLE;                                                     \
  .endm

#define RVTEST_RV32UV                                                          \
  .macro init;                                                                 \
  RVTEST_VECTOR_ENABLE;                                                        \
  .endm

#define INIT_XREG                                                              \
  li x1, 0;                                                                    \
  li x2, 0;                                                                    \
  li x3, 0;                                                                    \
  li x4, 0;                                                                    \
  li x5, 0;                                                                    \
  li x6, 0;                                                                    \
  li x7, 0;                                                                    \
  li x8, 0;                                                                    \
  li x9, 0;                                                                    \
  li x10, 0;                                                                   \
  li x11, 0;                                                                   \
  li x12, 0;                                                                   \
  li x13, 0;                                                                   \
  li x14, 0;                                                                   \
  li x15, 0;                                                                   \
  li x16, 0;                                                                   \
  li x17, 0;                                                                   \
  li x18, 0;                                                                   \
  li x19, 0;                                                                   \
  li x20, 0;                                                                   \
  li x21, 0;                                                                   \
  li x22, 0;                                                                   \
  li x23, 0;                                                                   \
  li x24, 0;                                                                   \
  li x25, 0;                                                                   \
  li x26, 0;                                                                   \
  li x27, 0;                                                                   \
  li x28, 0;                                                                   \
  li x29, 0;                                                                   \
  li x30, 0;                                                                   \
  li x31, 0;

#define INIT_PMP                                                               \
  la t0, 1f;                                                                   \
  csrw mtvec, t0;                                                              \
  /* Set up a PMP to permit all accesses */                                    \
  li t0, (1 << (31 + (__riscv_xlen / 64) * (53 - 31))) - 1;                    \
  csrw pmpaddr0, t0;                                                           \
  li t0, PMP_NAPOT | PMP_R | PMP_W | PMP_X;                                    \
  csrw pmpcfg0, t0;                                                            \
  .align 2;                                                                    \
  1:

#define INIT_SATP                                                              \
  la t0, 1f;                                                                   \
  csrw mtvec, t0;                                                              \
  csrwi satp, 0;                                                               \
  .align 2;                                                                    \
  1:

#define DELEGATE_NO_TRAPS                                                      \
  csrwi mie, 0;                                                                \
  la t0, 1f;                                                                   \
  csrw mtvec, t0;                                                              \
  csrwi medeleg, 0;                                                            \
  csrwi mideleg, 0;                                                            \
  .align 2;                                                                    \
  1:

#define RVTEST_ENABLE_SUPERVISOR                                               \
  li a0, MSTATUS_MPP &(MSTATUS_MPP >> 1);                                      \
  csrs mstatus, a0;                                                            \
  li a0, SIP_SSIP | SIP_STIP;                                                  \
  csrs mideleg, a0;

#define RVTEST_ENABLE_MACHINE                                                  \
  li a0, MSTATUS_MPP;                                                          \
  csrs mstatus, a0;

#define RVTEST_FP_ENABLE                                                       \
  li a0, MSTATUS_FS &(MSTATUS_FS >> 1);                                        \
  csrs mstatus, a0;                                                            \
  csrwi fcsr, 0

#define RVTEST_FP_VECTOR_ENABLE                                                \
  li a0, (MSTATUS_VS & (MSTATUS_VS >> 1)) | (MSTATUS_FS & (MSTATUS_FS >> 1));  \
  csrs mstatus, a0;                                                            \
  csrwi fcsr, 0;                                                               \
  csrwi vcsr, 0;

#define RVTEST_VECTOR_ENABLE                                                   \
  li a0, (MSTATUS_VS & (MSTATUS_VS >> 1)) | (MSTATUS_FS & (MSTATUS_FS >> 1));  \
  csrs mstatus, a0;                                                            \
  csrwi vcsr, 0;

#define RISCV_MULTICORE_DISABLE                                                \
  csrr a0, mhartid;                                                            \
  1 : bnez a0, 1b

#define EXTRA_TVEC_USER
#define EXTRA_TVEC_MACHINE
#define EXTRA_INIT
#define EXTRA_INIT_TIMER

#define INTERRUPT_HANDLER j other_exception /* No interrupts should occur */

#define RVTEST_CODE_BEGIN                                                      \
  .align 6;                                                                    \
  .weak stvec_handler;                                                         \
  .weak mtvec_handler;                                                         \
  .globl _start;                                                               \
  _start:                                                                      \
  /* reset vector */                                                           \
  j reset_vector;                                                              \
  .align 2;                                                                    \
  reset_vector:                                                                \
  INIT_XREG;                                                                   \
  RISCV_MULTICORE_DISABLE;                                                     \
  init;

//-----------------------------------------------------------------------
// End Macro
//-----------------------------------------------------------------------

// Write our custom CSR msimend to exit simulation.
#define RVTEST_CODE_END                                                        \
  li x1, 0x10000000;                                                           \
  li x2, 0xdeadbeef;                                                           \
  sw x2, 0(x1);                                                                \
  j .;

//-----------------------------------------------------------------------
// Pass/Fail Macro
//-----------------------------------------------------------------------

#define RVTEST_PASS
#define RVTEST_FAIL

//-----------------------------------------------------------------------
// Data Section Macro
//-----------------------------------------------------------------------

#define EXTRA_DATA \
        .section .vdata, "aw", @progbits; \

#define RVTEST_DATA_BEGIN                                                      \
  EXTRA_DATA.align 4;                                                          \
  .global begin_signature;                                                     \
  begin_signature:

#define RVTEST_DATA_END                                                        \
  .align 4;                                                                    \
  .global end_signature;                                                       \
  end_signature:

#endif
