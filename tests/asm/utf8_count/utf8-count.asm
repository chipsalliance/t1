# src: https://github.com/camel-cdr/rvv-bench/blob/main/bench/utf8_count.S
# modifications:
# 1. add test to call memcpy
# 2. add exit to finish simulation

.section .vdata, "aw", @progbits
.balign 64
string:
.string "这里是一段 UTF-8 编码的文字，用来测试 utf8_count 函数的 RVV 实现。"
string_end:
.byte 0

.text
.balign 16
.globl test

# size_t utf8_count(char const *str, size_t len) 
# a0=str, a1=len
utf8_count:
    li a2, 0                         # Initialize count of UTF-8 characters to 0
    li a3, -65                       # Set the threshold value for identifying UTF-8 lead bytes
utf8_count_loop:
    vsetvli a4, a1, e8, m8, ta, ma   # Set vector length for processing, using byte (e8) elements
    vle8.v v8, (a0)                  # Load a vector of bytes from the string pointed by a0
    vmsgt.vx v16, v8, a3             # Set mask for elements in v8 greater than a3 (-65), identifying lead UTF-8 bytes
    vcpop.m a5, v16                  # Count the number of set bits in the mask, representing the UTF-8 lead bytes
    add a2, a2, a5                   # Accumulate the count of UTF-8 characters
    sub a1, a1, a4                   # Decrement the remaining byte count by the number processed in this iteration
    add a0, a0, a4                   # Increment the string pointer by the number of bytes processed
    bnez a1, utf8_count_loop         # If there are more bytes to process, continue the loop
    mv a0, a2                        # Move the final count of UTF-8 characters to a0 (return value)
    ret                              # Return from the function

test:
    addi sp, sp, -4
    sw ra, 0(sp)

    la a1, string_end                # Load the address of the string end into a1
    la a0, string                    # Load the address of the string into a0
    sub a1, a1, a0                   # Subtract the start address from the end address to get the length
    addi a1, a1, -1                  # Subtract 1 for the null terminator
    # Call the utf8_count function to count the number of UTF-8 characters in the string
    # a0: char const *str, a1: size_t len
    call utf8_count

    lw ra, 0(sp)
    addi sp, sp, 4
    ret

