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

## Future Work
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
- FGMT scalar code for handling interrupt during vector SIMD being running;
- MMU support;
- MSP support;

## Development Guide

The IP emulators are designed to emulate the vector IP. Spike is used as the scalar core integrating with verilated vector IP and use difftest for IP-level verification, it records events from VRF and LSU to perform online difftest.

### Nix setup
We use nix flake as our primary build system. If you have not installed nix, install it following the [guide](https://nixos.org/manual/nix/stable/installation/installing-binary.html), and enable flake following the [wiki](https://nixos.wiki/wiki/Flakes#Enable_flakes). Or you can try the [installer](https://github.com/DeterminateSystems/nix-installer) provided by Determinate Systems, which enables flake by default.

### Build

t1 includes a hardware design written in Chisel and a emulator powered by verilator. The elaborator and emulator can be run with various configurations. Each configuration is specified with a JSON file in `./configs` directory. We can specify a configuration by its JSON file name, e.g. `v1024-l8-b2`.

You can build its components with the following commands:
```shell
$ nix build .#t1.elaborator  # the wrapped jar file of the Chisel elaborator

$ nix build .#t1.<config-name>.ip.rtl  # the elaborated IP core .sv files
$ nix build .#t1.<config-name>.ip.emu-rtl  # the elaborated IP core .sv files with emulation support
$ nix build .#t1.<config-name>.ip.emu  # build the IP core emulator
$ nix build .#t1.<config-name>.ip.emu-trace  # build the IP core emulator with trace support

$ nix build .#t1.<config-name>.subsystem.rtl  # the elaborated soc .sv files
$ nix build .#t1.<config-name>.subsystem.emu-rtl  # the elaborated soc .sv files with emulation support
$ nix build .#t1.<config-name>.subsystem.emu  # build the soc emulator
$ nix build .#t1.<config-name>.subsystem.emu-trace  # build the soc emulator with trace support
$ nix build .#t1.<config-name>.subsystem.fpga  # build the elaborated soc .sv files with fpga support

$ nix build .#t1.rvv-testcases.all  # the testcases
```
where `<config-name>` should be replaced with a configuration name, e.g. `v1024-l8-b2`. The build output will be put in `./result` directory by default.

#### Run Testcases

To run testcases, set `TEST_CASES_DIR` to the directory containing built testcases, either manually or using the following commands:
```shell
$ nix develop  # enter the default development shell with common tools
```

Now run the job using the following script:
```shell
$ ./scripts/run-test.py verilate -c <config> -r <runConfig> <caseName>
```
wheres
- `<config>` is the configuration name, filename in `./configs`;
- `<caseName>` is the name of a testcase, filename in `$TEST_CASES_DIR/configs`;
- `<runConfig>` is a emulator running config, described as filename in `./run`.

For example:
```shell
./scripts/run-test.py verilate -c v1024-l8-b2 -r debug conv-mlir  # '-r debug' can be omitted since it is the default
```

`run-test.py` provides various command-line options for different use cases. Run `./scripts/run-test.py -h` for help.

### Development

#### Developing Elaborator (Chisel-only)
```shell
$ nix develop .#t1.elaborator  # bring up scala environment, circt tools, and create submodule soft links

$ nix develop .#t1.elaborator.editable  # or if you want submodules editable

$ mill -i elaborator  # build and run elaborator
```

#### Developing Emulator
```shell
$ nix develop .#t1.<config>.ip.emu  # replace <config> with your configuration name
$ cd emulator
$ cmake -B build -GNinja -DCMAKE_BUILD_TYPE=Debug
$ cmake --build build
$ cd ..; ./scripts/run-test.py verilate --emulator-path=emulator/src/build/emulator conv-mlir
```

#### Developing Testcases
The `tests/` contains the testcases. There are four types of testcases:

- asm
- intrinsic
- mlir
- codegen

To add new testcases for asm/intrinsic/mlir, create a new directory with `default.nix` and source files.
Refer to the existing code for more information on how to write the nix file.

To add new testcases for codegen type cases, add new entry in `codegen/*.txt`, then our nix macro will automatically populate new testcases to build.

To view what is available to ran, use the `nix search` sub command:

```console
# nix search .#t1 <regexp>
#
# For example:
$ nix search .#t1 asm
* legacyPackages.x86_64-linux.t1.rvv-testcases.asm.fpsmoke
  Test case 'fpsmoke', written in assembly.

* legacyPackages.x86_64-linux.t1.rvv-testcases.asm.memcpy
  Test case 'memcpy', written in assembly.

* legacyPackages.x86_64-linux.t1.rvv-testcases.asm.mmm
  Test case 'mmm', written in assembly.

* legacyPackages.x86_64-linux.t1.rvv-testcases.asm.smoke
  Test case 'smoke', written in assembly.

* legacyPackages.x86_64-linux.t1.rvv-testcases.asm.strlen
  Test case 'strlen', written in assembly.

* legacyPackages.x86_64-linux.t1.rvv-testcases.asm.utf8-count
  Test case 'utf8-count', written in assembly.

# Then ignore the `legacyPackage.x86_64-linux` attribute, build the testcase like below:
$ nix build .#t1.rvv-testcases.asm.smoke
```

To develop a specific testcases, enter the development shell:

```shell
# nix develop .#t1.rvv-testcases.<type>.<name>
#
# For example:

$ nix develop .#t1.rvv-testcases.asm.smoke
```

Build tests:

```shell
# build a single test
$ nix build .#t1.rvv-testcases.intrinsic.matmul -L
$ ls -al ./result

# build all tests
$ nix build .#t1.rvv-testcases.all --max-jobs $(nproc)
$ ls -al ./result
```

> All the `mk*Case` expression are defined in `./nix/t1/default.nix`.

### Bump Dependencies
Bump nixpkgs:
```shell
$ nix flake update
```

Bump chisel submodule versions:
```shell
$ cd nix/t1
$ nvfetcher
```

## License
Copyright © 2022-2023, Jiuyang Liu. Released under the Apache-2.0 License.

