#import "template.typ": project

#show: project.with(
  title: "T1 docker manual",
)

= Using the Emulator with Docker
The emulator environment provides a set of tools and examples to help users get
started. Below are detailed instructions and explanations for compiling and
running test cases.

== Examples
There are four sample source code files located in the /workspace/examples
directory. The T1 runtime stubs are placed under /workspace/share.

This Docker container includes a Clang wrapper that simplifies the compilation
process by handling most compiler options. Users can easily compile the example
source code using the following commands:

```bash
cd /workspace/intrinsic.linear_normalization
t1-cc -T /workspace/share/t1.ld /workspace/share/main.S linear_normalization.c -o linear_normalization.elf
t1emu-verilated-simulator +t1_elf_file=linear_normalization.elf
```

== Marking Memory Regions
The t1.ld file specifies how the linker organizes the memory layout. We use the following memory configurations:

- *Scalar memory*: Starts at 0x20000000 with a size of 512MB.
- *Vector memory*: Starts at 0x60000000 with a size of 1024MB.

Developers can use the `__attribute((section(".vbss")))` attribute to mark
regions of memory that need to be copied to SRAM. For example, in
linear_normalization.c:

```c
#define ARRAY_ZIZE 1024
__attribute((section(".vbss"))) float actual[ARRAY_ZIZE];
```

== Main Function
The main.S file acts as the main function. It initializes all registers before
running a test case and then jumps to the test symbol. Developers writing new
test cases should use the following structure for their entry point:

```c
int test() {
    // Test implementation
}
```

== Clang Wrapper
The `t1-cc` command wraps several Clang options for convenience:

```bash
riscv32-none-elf-clang \
    -I/path/to/t1-runtime/include -L/path/to/t1-runtime/lib \
    -mabi=ilp32f -march=rv32gc_zvl2048b_zve32f -mno-relax -static -mcmodel=medany \
    -fvisibility=hidden -fno-PIC -g -O3 -frandom-seed=<random string>
```

== Simulator Variants
The simulator binary may differ based on the container used:

- Containers with the suffix *t1rocketemu* include the t1rocketemu-verilated-simulator, which uses the Rocket core.
- Containers with the suffix *t1emu* include the t1emu-verilated-simulator, which handles scalar instructions using Spike.

*Note*: MMIO (Memory-Mapped I/O) support is currently unavailable in containers
suffixed with -t1emu. Consequently, features like printf or framebuffer will
not work in these containers.
