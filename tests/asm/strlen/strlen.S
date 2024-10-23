# src: https://github.com/camel-cdr/rvv-bench/blob/main/bench/strlen.S
# modifications:
# 1. add test to call strlen
# 2. add memset to fill memory
# 3. add exit to finish simulation

.text
.balign 16
.globl test

# size_t strlen(const char *str)
# a0=str
strlen:
    mv a3, a0                        # Copy the string pointer from a0 to a3

strlen_loop:
    vsetvli a1, x0, e8, m8, ta, ma   # Set vector length for maximum available bytes, with 8-bit element size
    vle8ff.v v8, (a3)                # Load a vector of bytes from the string, fault-only-first (continue past page faults)
    csrr a1, vl                      # Read the actual vector length (number of bytes loaded) into a1
    vmseq.vi v0, v8, 0               # Set vector mask v0 for elements in v8 that are equal to 0 (null terminator)
    vfirst.m a2, v0                  # Find the first masked element (null terminator position) and store in a2
    add a3, a3, a1                   # Increment the string pointer by the vector length processed
    bltz a2, strlen_loop             # If null terminator not found (a2 < 0), continue the loop
    add a0, a0, a1                   # Add the vector length to the original string pointer (accumulating total length)
    add a3, a3, a2                   # Adjust the end pointer by the index of the null terminator
    sub a0, a3, a0                   # Calculate the string length by subtracting the original pointer from the end pointer
    ret                              # Return with the string length in a0

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

    # fill 0x1000000 with 0x55 x 4096 bytes
    # a0: void* dest, a1: int n, a2: size_t len
    la a0, test_str_start
    li a1, 0x55
    li a2, 0x1000
    call memset
    # Call strlen to calculate the length of the string starting at memory address 0x1000000
    # a0: const char *str
    la a0, test_str_start
    call strlen

    lw ra, 0(sp)
    addi sp, sp, 4
    ret

.section .vbss, "aw", @nobits
.balign 64
test_str_start:
    .zero 4096
test_str_end:
    .zero 1
