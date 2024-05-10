# src: https://github.com/riscv/riscv-v-spec/blob/master/example/memcpy.s
# modifications:
# 1. add test to call memcpy
# 2. add memset to fill memory
# 3. add exit to finish simulation

.text
.balign 16
.globl test

# void *memcpy(void* dest, const void* src, size_t n)
# a0=dest, a1=src, a2=n
memcpy:
    mv a3, a0                        # Initialize a3 with the destination address
memcpy_loop:
    vsetvli t0, a2, e8, m8, ta, ma   # Set up the vector configuration for the current loop iteration. 
                                     # t0 holds the actual vector length processed, a2 is the remaining byte count, 
                                     # e8 specifies 8-bit elements, m8 for masking, ta and ma for tail and mask agnostic settings
    vle8.v v0, (a1)                  # Load a vector of bytes from the source address pointed by a1 into vector register v0
    add a1, a1, t0                   # Increment the source address pointer by the number of bytes processed in this iteration
    sub a2, a2, t0                   # Reduce the remaining byte count by the number processed in this iteration
    vse8.v v0, (a3)                  # Store the vector of bytes from vector register v0 to the destination address pointed by a3
    add a3, a3, t0                   # Increment the destination address pointer by the number of bytes processed
    bnez a2, memcpy_loop             # Repeat the loop if there are more bytes to copy
    ret                              # Return from the function

# void *memset(void* dest, int n, size_t len)
# a0=dest, a1=n, a2=len
memset:
    vsetvli a3, zero, e8, m8, ta, ma # Set vector length to max, element width to 8 bits, using mask 'm8'
    vmv.v.x v8, a1                   # Move the value in a1 (the byte to set) to all elements of vector register v8
    mv a1, a0                        # Move the destination address from a0 to a1

memset_loop:
    vsetvli a3, a2, e8, m8, ta, ma   # Set vector length for this loop iteration, element width 8 bits
    vse8.v v8, (a1)                  # Store 8-bit values from vector register v8 to memory at address in a1
    sub a2, a2, a3                   # Subtract the number of bytes processed in this iteration from the total count
    add a1, a1, a3                   # Add the number of bytes processed to the destination pointer
    bnez a2, memset_loop             # If there are more bytes to process, loop again
    ret                              # Return from the function

test:
    addi sp, sp, -4
    sw ra, 0(sp)

    # fill 0x1001000 with 0x55 x 4096 bytes
    # a0: void* dest, a1: int n, a2: size_t len
    lw a0, test_src_start
    li a1, 0x55
    li a2, 0x1000
    call memset
    # copy 0x1001000 to 0x1000000 with 4096 bytes
    # a0: void* dest, a1: void* src, a2: size_t n
    lw a0, test_src_start
    lw a1, test_dst_start
    li a2, 0x1000
    call memcpy

    lw ra, 0(sp)
    addi sp, sp, 4
    ret

.section .vbss, "aw", @nobits
.balign 64
test_src_start:
    .zero 4096
test_dst_start:
    .zero 4096

