    .globl start
start:
    li a0, 0x200 # MSTATUS_VS & (MSTATUS_VS >> 1)
    csrs mstatus, a0
    csrwi vcsr, 0

    la sp, stack_end

    call test
    csrwi 0x7cc, 0

    .p2align 2
stack_start:
    .zero 128

stack_end:

# Add some print functions' definitions here
# to support direct lowering of such func.call and vector.print 
printMemrefI32:
    ret
printMemrefI64:
    ret
printMemrefF32:
    ret
printMemrefF64:
    ret
printMemrefInd:
    ret
printMemrefC32:
    ret
printMemrefC64:
    ret
printCString:
    ret
printI64:
    ret
printU64:
    ret
printF32:
    ret
printF64:
    ret
printOpen:
    ret
printClose:
    ret
printComma:
    ret
printNewline:
    ret
