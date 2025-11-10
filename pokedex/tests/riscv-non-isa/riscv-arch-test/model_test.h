#ifndef _COMPLIANCE_MODEL_H
#define _COMPLIANCE_MODEL_H

#define XLEN 32
#define FLEN 32
#define ZHINX 1
#define ALIGNMENT 2

#define MMIO_EXIT pokedex_mmio_exit

#define RVMODEL_DATA_SECTION                                                   \
        .pushsection .tohost,"aw",@progbits;                                   \
        .align 5; .global tohost; tohost: .word 0; .size tohost, 4;            \
        .align 5; .global fromhost; fromhost: .word 0; .size fromhost, 4;      \
        .align 5; .global MMIO_EXIT; MMIO_EXIT: .word 0; .size MMIO_EXIT, 4;   \
        .popsection;

// RV_COMPLIANCE_HALT
#define RVMODEL_HALT                                                           \
  li x1, 0;                                                                    \
  write_tohost:                                                                \
  sw x1, tohost, t5;                                                           \
  j .;

#define RVMODEL_BOOT

// RV_COMPLIANCE_DATA_BEGIN
#define RVMODEL_DATA_BEGIN                                                     \
  RVMODEL_DATA_SECTION.align ALIGNMENT;                                        \
  .global begin_signature;                                                     \
  begin_signature:

// RV_COMPLIANCE_DATA_END
#define RVMODEL_DATA_END                                                       \
  .global end_signature;                                                       \
  end_signature:

// RVTEST_IO_INIT
#define RVMODEL_IO_INIT
// RVTEST_IO_WRITE_STR
#define RVMODEL_IO_WRITE_STR(_R, _STR)
// RVTEST_IO_CHECK
#define RVMODEL_IO_CHECK()
// RVTEST_IO_ASSERT_GPR_EQ
#define RVMODEL_IO_ASSERT_GPR_EQ(_S, _R, _I)
// RVTEST_IO_ASSERT_SFPR_EQ
#define RVMODEL_IO_ASSERT_SFPR_EQ(_F, _R, _I)
// RVTEST_IO_ASSERT_DFPR_EQ
#define RVMODEL_IO_ASSERT_DFPR_EQ(_D, _R, _I)

#define RVMODEL_SET_MSW_INT

#define RVMODEL_CLEAR_MSW_INT

#define RVMODEL_CLEAR_MTIMER_INT

#define RVMODEL_CLEAR_MEXT_INT

#endif // _COMPLIANCE_MODEL_H
