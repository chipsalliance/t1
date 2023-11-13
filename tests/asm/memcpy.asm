# src: https://github.com/riscv/riscv-v-spec/blob/master/example/memcpy.s
# modifications:
# 1. add test to call memcpy
# 2. add exit to finish simulation

.text
.balign 16
.globl test
# void *memcpy(void* dest, const void* src, size_t n)
# a0=dest, a1=src, a2=n
#
memcpy:
    mv a3, a0 # Copy destination
loop:
    vsetvli t0, a2, e8, m8, ta, ma  # Vectors of 8b
    vle8.v v0, (a1)                 # Load bytes
    add a1, a1, t0                  # Bump pointer
    sub a2, a2, t0                  # Decrement count
    vse8.v v0, (a3)                 # Store bytes
    add a3, a3, t0                  # Bump pointer
    bnez a2, loop                   # Any more?
    ret                             # Return

fill_memory:
    li a0, 0x10010000               # Load the starting address into register a0
    li a1, 0x55                     # Load the fill value 0x55 into register a1
    li a2, 0x1000                   # Load the length 0x1000 (4096 bytes) into register a2

fill_loop:
    sb a1, 0(a0)                    # Store the value from register a1 into the memory address pointed by a0
    addi a0, a0, 1                  # Increment the address in register a0 by 1 to move to the next byte
    addi a2, a2, -1                 # Decrement the length in register a2 by 1
    bnez a2, fill_loop              # If the length is not zero, continue the loop
    ret                             # Return to the caller

check_memory:
    li a0, 0x1000000                # Load the starting address into register a0
    li a3, 0x1000                   # Load the length 0x1000 (4096 bytes) into register a3
    li a1, 0x55                     # Load the value to check against into register a1

check_loop:
    lbu a2, 0(a0)                   # Load the byte from memory at address in a0 into a2
    bne a1, a2, fail                # If the loaded byte is not equal to 0x55, branch to fail
    addi a0, a0, 1                  # Increment the address in a0 to check the next byte
    addi a3, a3, -1                 # Decrement the length in a3
    bnez a3, check_loop             # If we haven't checked all bytes, loop again

    li a0, 0xff                     # If all bytes were 0x55, set a0 to 0xff
    ret                             # Return from the function

fail:
    li a0, 0xfe                     # If any byte was not 0x55, set a0 to 0xfe
    ret                             # Return from the function

test:
    # fill 0x1001000 with 0x55 x 4096 bytes
    call fill_memory
    # a0: void* dest, a1: void* src, a2: size_t n
    li a0, 0x1000000
    li a1, 0x1001000
    li a2, 0x1000
    call memcpy
    call check_memory
    # a0: 0xff if success, 0xfe if fail
    # TODO: how to pass result to CI?
exit:
    # Write msimend to exit simulation.
    csrwi 0x7cc, 0
