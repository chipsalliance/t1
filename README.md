# The RISC-V Long Vector Machine Hardware Generator

Welcome to our project (name to be announced). We're developing a RISC-V Long Vector Machine Hardware Generator using the Chisel hardware construction language. It is fully compliant with the RISC-V Vector Spec 1.0, and currently supports the Zvl1024b and Zve32x extensions. The architecture is inspired by the Cray X1 vector machine.

## Architecture Highlights:

The generated vector processors can integrate with any RISC-V scalar core that uses the Out-of-Order (OoO) write-back scheme.

### Lanes Basic Profiles:
- Default support for multiple lanes.
- Vector Register File (VRF) based on Dual-port Static RAM (SRAM).
- Fully pipelined Vector Function Unit (VFU) with comprehensive chaining support. We allocate 4 VFU slots per VRF.
- Our design can skip masked elements for mask instructions to accommodate the sparsity of the mask.
- We use a ring-based lane interconnection for widen instructions.
- No rename unit is implemented.

### Load Store Unit (LSU) Profiles:

- Configurable banked memory port with a banked TileLink-based vector cache.
- Instruction-level Out-of-Order (OoO) load/store, leveraging the high memory bandwidth of the vector cache.
- Configurable outstanding size to mitigate memory latency.
- Fully chained to the Vector Function Unit (VFU).

## Design Space Exploration (DSE) Principles and Methodology:

Compared to some commercial Out-of-Order core designs with advanced prediction schemes, the architecture of the vector machine is relatively straightforward. Instead of dedicating area to a Branch Prediction Unit (BPU) or Rename and Reorder Buffer (ROB), vector instructions provide enough data to allow the VPU to run for thousands of cycles without requiring a prediction scheme.

The VPU is designed to balance bandwidth, area, and frequency among the VRF, VFU, and LSU. With this generator, the vector machine can be easily configured to achieve either high efficiency or high performance, depending on the desired trade-offs.

The methodology for microarchitecture tuning based on this trade-off idea includes:

**The overall vector core frequency should be limited by the VRF memory frequency**: Based on this principle, the VFU pipeline should be retimed to multiple stages to meet the frequency target. For a small, highly efficient core, designers should choose high-density memory (which usually doesn't offer high frequency) and reduce the VFU pipeline stages. For a high-performance core, they should increase the pipeline stages and use the fastest possible SRAM for the VRFs.

**The bandwidth bottleneck is limited by VRF SRAM**: For each VFU, if it is operating, it might encounter hazards due to the limited VRF memory ports. To resolve this, we strictly limit each VRF bank to 4 VFUs. In the v1p0 release, this issue should be resolved by a banked VRF design. But the banked VRF creates an all-to-all crossbar between the VFU and VRF banks, which is heavily constrained by the physical design.

**The bandwidth of the LSU is limited by the memory ports to the Vector Cache**: The LSU is also configurable to provide the required memory bandwidth: it is designed to support multiple vector cache memory banks. Each memory bank has 3 MSHRs for outstanding memory transactions, which are limited by the VRF memory ports. Our design supports instruction-level interleaved vector load/store to maximize the use of memory ports for high memory bandwidth.

**The upward bandwidth of the Vector Cache is limited by the bank size**: The vector cache microarchitecture essentially has multiple banks with relatively small cache line sizes. It is always constrained by physical design.

For tuning the ideal vector machines, follow these performance tuning methodologies:

- Determine the required VLEN as it dictates the VRF memory area.
- Choose the memory type for the VRF, which will determine the chip frequency.
- Determine the required bandwidth. This will decide the bandwidth for VRF, VFU, LSU. Benchmarks for specific programs are needed to balance the bandwidth for these components. The lane size is also decided at this stage.
- Decide on the vector cache size. Specific workloads are required to benchmark the miss rate.
- Configure the vector cache microarchitecture. This is essentially determined by the next level memory subsystems.

# Future Work
The v1p0 will tapeout in 2023 with TSN28 process. This tapeout is the silicon verification for our architecture and design flows.

### Before v1p0 tapeout
- Banked VRF support(increase bandwidth for VRF);
- VRF memory with ECC support(no complex MBIST design for VRF);
- Datapath for merging Int8 to Int32 pipeline(increase bandwidth for VFU);
- Burst support for unit-stride(saves bandwidth in TL-A Channel);
- Configurable VFU type and size(tune performance).

### After v1p0 tapeout
- 64-bits support;
- IEEE-754 FPU support;
- FGMT scalar code for handling interupt during vector SIMD being running;
- MMU support;
- MSP support;

## Build and Development

The IP emulators are designed to emulate the vector IP. Spike is used as the scalar core integrating with verilated vector IP and use difftest for IP-level verification, it records events from VRF and LSU to perform online difftest.

You can find different cases under `tests/cases` folder, they are dynamically detected found by the `nix` build system.

### Nix setup
We use nix flake to setup test environment. If you have not installed nix, install it following the [guide](https://nixos.org/manual/nix/stable/installation/installing-binary.html), and enable flake following the [wiki](https://nixos.wiki/wiki/Flakes#Enable_flakes). Or you can try the [installer](https://github.com/DeterminateSystems/nix-installer) provided by Determinate Systems, which enables flake automatically.

### Build

t1 includes a hardware design written in Chisel and a emulator powered by verilator. The elaborator and emulator can be run with different configurations. Each configuration is specified with a JSON file in `./configs` directory. We can specify a configuration by its JSON file name, e.g. `v1024l8p2fp-test`.

You can build its components with the following commands:
```shell
$ nix build .#t1.elaborator  # the wrapped jar file of the Chisel elaborator
$ nix build .#t1.<config-name>.elaborate  # the elaborated .sv files
$ nix build .#t1.<config-name>.verilator-emulator  # the verilator emulator

$ nix build .#t1.rvv-testcases  # the testcases
```
where `<config-name>` should be replaced with a configuration name, e.g. `v1024l8p2fp-test`.

### Development

#### Developing Elaborator (Chisel-only)
```shell
$ nix develop .#t1.elaborator

$ nix develop .#t1.elaborator.editable  # or if you want submodules editable

$ mill -i elaborator  # build and run elaborator
```

#### Developing Emulator
```shell
$ nix develop .#t1.v1024l8b2-test-trace.verilator-emulator
$ cd emulator/src
$ cmake -B build -GNinja -DCMAKE_BUILD_TYPE=Debug
$ cmake --build build
```

#### Developing Testcases
The `tests/` directory itself is a single build module.
There are four types of tests in this build module:

- asm
- intrinsic
- mlir
- codegen

To control how these tests are run, there are separate configs for each test in the `configs/` directory.
The `mill` build system will attempt to compile the `build.sc` file and generate test case objects
by reading the test case source file and its corresponding config file.

The codegen tests requires [ksco/riscv-vector-tests](https://github.com/ksco/riscv-vector-tests) to generate multiple asm tests.
If you want to build all the codegen tests manually, you will need to set the following environment variable:

- `CODEGEN_BIN_PATH`: Path to the `single` binary in riscv-vector-tests.
- `CODEGEN_INC_PATH`: A list of header include paths. You may need to set it as `export CODEGEN_INC_PATH="$codegen/macro/sequencer-vector $codegen/env/sequencer-vector`.
- `CODEGEN_CFG_PATH`: Path to the `configs` directory in riscv-vector-tests.

Enter the development shell with above environment variables set:
```shell
$ nix develop .#t1.testcases
```

List available tests:
```shell
$ mill -i resolve _[_].elf

# Get asm test only
$ mill -i resolve asm[_].elf
```

Build tests:
```shell
# build a single test
$ mill -i asm[hello-mlir].elf

# build all tests
$ cd tests
$ ./buildAll.sc . path/to/output/directory
```

Run testcases in the development shell:
```shell
$ nix develop  # enter the default development shell with testcases built by nix
$ ./scripts/run-test.py -c v1024l8b2-test conv-mlir
$ ./scripts/run-test.py --help  # see all options
```

### Bump Dependencies
Bump nixpkgs:
```shell
$ nix flake update
```

Bump submodule versions:
```shell
$ cd nix/t1
$ nvfetcher
```

## License
Copyright © 2022-2023, Jiuyang Liu. Released under the Apache-2.0 License.

