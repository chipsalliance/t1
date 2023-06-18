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
.globl printMemrefI32
printMemrefI32:
        ret
.globl printMemrefI64
printMemrefI64:
        ret
.globl printMemrefF32
printMemrefF32:
        ret
.globl printMemrefF64
printMemrefF64:
        ret
.globl printMemrefInd
printMemrefInd:
        ret
.globl printMemrefC32
printMemrefC32:
        ret
.globl printMemrefC64
printMemrefC64:
        ret
.globl printCString
printCString:
        ret
.globl printI64
printI64:
        ret
.globl printU64
printU64:
        ret
.globl printF32
printF32:
        ret
.globl printF64
printF64:
        ret
.globl printOpen
printOpen:
        ret
.globl printClose
printClose:
        ret
.globl printComma
printComma:
        ret
.globl printNewline
printNewline:
        ret
