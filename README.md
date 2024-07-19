# T1

T1(Torrent-1) is a RISC-V Vector implementation inspired by the Cray X1 vector machine, which is named after [T0](https://www2.eecs.berkeley.edu/Pubs/TechRpts/1997/5411.html).

T1 aims to implement the RISC-V Vector in lane-based micro-architectures, with intensive chaining support and SRAM-based VRFs.

T1 supports standard `Zve32f` and `Zve32x`, and `VLEN`/`DLEN` can be increased up to `64K`, hitting the RISC-V Vector architecture bottleneck.

T1 ships important vector machine features, e.g., lanes, chaining, and large LSU outstanding by default, but it can also be a general platform for MMIO DSA(Domain-Specific-Accelerators).

T1 is designed with [Chisel](https://github.com/chipsalliance/chisel) and releasing `IPEmulator` to users.

T1 uses a forked version of the Rocket Core as the scalar part of T1. But we don't officially support it for now; it can be replaced by any other RISC-V Scalar CPU.

T1 only supports bare-metal program loading and execution; test examples can be found in the `tests/` folder.

## Architecture Highlights:

The generated T1 vector processors can integrate with any RISC-V scalar core.

### Lanes Basic Profiles:
- Default support for multiple lanes(32-bits per-lane).
- Load to Multiple-Exec to Store to Load chaining-ability.
- RAM-based configurable banked SRAM with DualPort, TwoPort, and SinglePort supports.
- Pipelined/Asynchronous Vector Function Unit (VFU) with comprehensive chaining support. Allocating 4 VFU slots per lane, multiple and different VFU can be attached to the corresponding lane.
- T1 lane execution can skip masked elements for the mask instructions that are all masked to accommodate the sparsity of the mask.
- We use a direct-connected lane interconnection for `widen` and `narrow` instructions.

### Load Store Unit (LSU) Profiles:

- Configurable banked memory port.
- Instruction-level Out-of-Order (OoO) load/store, leveraging the high memory bandwidth of the vector cache.
- Configurable outstanding size to mitigate memory latency.
- Fully chained to the Vector Function Unit (VFU).

## Design Space Exploration (DSE) Principles and Methodology:

Compared to some commercial Out-of-Order core designs with advanced speculation schemes, the architecture of the vector machine is relatively straightforward. Instead of dedicating the area to a Branch Prediction Unit (BPU), Rename and Reorder Buffer(ROB) or prefetching. Vector instructions provide enough metadata to allow T1 to run for thousands of elements without requiring a speculation scheme.

T1 is designed to balance the throughput, area, and frequency among the VRF, VFU, and LSU. With the T1 generator, it can be easily configured to achieve either high efficiency or high performance, depending on the desired trade-offs, even adding function units or purging out FPU, which supports `Zve32f` and remains `Zve32x` only.

The methodology for the micro-architecture tuning is based on this trade-off idea:

**The overall vector core frequency should be limited by the VRF memory**. Based on this principle, we could retime the VFU pipeline to multiple stages to meet the frequency target. For a small, highly efficient core, designers should choose high-density memory (which usually doesn’t offer high frequency) and reduce the VFU pipeline stages. For a high-performance core, they should increase the pipeline stages and use the fastest possible SRAM for the VRFs.

**The bandwidth bottleneck is limited by VRF SRAM**. For each VFU, if it is operating, it might encounter hazards due to the limited VRF memory ports. Users can increase the banking size of VRFs. The banked VRF is forcing an all-to-all crossbar between the VFU and VRF banks, which has a heavy impact on the physical design. Users should trade off the Exec and VRF bandwidth by limiting the connection between Execution and VRFs.

**The bandwidth of the LSU is limited by the memory ports**: The LSU is also configurable to provide an insane memory bandwidth with a small overhead. It contains these limitations to bus:
- Requiring FIFO (first-in-first-out) ordering in bus. If FIFO is not implemented in the bus IP, a large reorder unit will be implemented due to extremely large outstanding `sourceId` in TileLink, like `AWID`, `ARID`, `WID`, `RID`, `BID` in AXI protocol.
- Requiring no-MMU for high-bandwidth-ports, since we may query `DLEN/32` elements from TLB for each cycle in an indexed load store mode, while there might be an unreasonable page fault outstandings. For now, these features are not supported in the current Rocket Core.
- No Coherence support: any high-performance cache cannot bear T1’s `DLEN/32` queries.

The key point of T1 LSU is that it is designed to support multiple memory banks. Each memory bank has 3 MSHRs for outstanding memory instructions, while every instruction can record thousands of transaction states in the FIFO order. T1 also supports instruction-level interleaved vector load/store to maximize the use of memory ports for high memory bandwidth.

For tuning the ideal vector machines, follow these performance-tuning methodologies:

- Determine DLEN for your parallelism requirement, AKA the required bandwidth for the Vector unit.
- Matching bandwidth for VRF, VFU, and LSU.
- Based on your workload, determine the required VLEN as it dictates the VRF memory area.
- Choose the memory type for the VRF, which will determine the chip frequency.
- Run the IPEmulator and PnR for your workloads to tune micro-architecture.

## Development Guide

We have a IP emulator under the directory `./ipemu`. [Spike](https://github.com/riscv/riscv-isa-sim) is used as the reference scalar core, integrated with the verilated vector IP. Under the online differential-test strategy, the emulator compares the load/store and VRF writes between Spike and T1 to verify T1’s correctness.

### Docker images

```bash
docker pull ghcr.io/chipsalliance/t1-$config:latest
# For example, config with dlen 256 vlen 512 support
docker pull ghcr.io/chipsalliance/t1-blastoise:latest
```

Or build the image using nix and load it into docker

```bash
nix build -L ".#t1.$config.release.docker-image" --out-link docker-image.tar.gz
docker load -i ./docker-image.tar.gz
```

> Using nix to build docker-image required KVM feature, so this derivation might not be available
> for some platform that has no QEMU/KVM support.

### Nix setup
We use Nix Flake as our primary build system. If you have not installed nix, install it following the [guide](https://nixos.org/manual/nix/stable/installation/installing-binary.html), and enable flake following the [wiki](https://nixos.wiki/wiki/Flakes#Enable_flakes). Or you can try the [installer](https://github.com/DeterminateSystems/nix-installer) provided by Determinate Systems, which enables flake by default.

### Build

T1 includes a hardware design written in Chisel and an emulator powered by a verilator. The elaborator and emulator can be run with various configurations. Configurations can be represented by your favorite Pokemon! The only limitation is that T1 uses [Pokemon type](https://pokemon.fandom.com/wiki/Types) to determine `DLEN`, aka lane size, based on the corresponding map:

|Type|DLEN|
|-|-|
|[Grass](https://bulbapedia.bulbagarden.net/wiki/Grass_(type))|32|
|[Fire](https://bulbapedia.bulbagarden.net/wiki/Fire_(type))|64|
|[Flying](https://bulbapedia.bulbagarden.net/wiki/Flying_(type))|128|
|[Water](https://bulbapedia.bulbagarden.net/wiki/Water_(type))|256|
|[Fighting](https://bulbapedia.bulbagarden.net/wiki/Fighting_(type))|512|
|[Electric](https://bulbapedia.bulbagarden.net/wiki/Electric_(type))|1K|
|[Ground](https://bulbapedia.bulbagarden.net/wiki/Ground_(type))|1K|
|[Psychic](https://bulbapedia.bulbagarden.net/wiki/Psychic_(type))|2K|
|[Dark](https://bulbapedia.bulbagarden.net/wiki/Rock_(type))|4K|
|[Ice](https://bulbapedia.bulbagarden.net/wiki/Ice_(type))|8K|
|[Fairy](https://bulbapedia.bulbagarden.net/wiki/Fairy_(type))|16K|
|[Ghost](https://bulbapedia.bulbagarden.net/wiki/Ghost_(type))|32K|
|[Dragon](https://bulbapedia.bulbagarden.net/wiki/Dragon_(type))|64K|

> [!NOTE]
> The `Bug` type is reserved to submit bug report by users.

Users can add their own pokemon to `configgen/src/Main.scala` to add configurations with different variations.

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

$ nix build .#t1.<config-name>.cases.all  # the testcases
```
where `<config-name>` should be replaced with a configuration name, e.g. `bulbasaur`. The build output will be put in `./result` directory by default.

Currently under tested configs:

| Config name   | Short summary                                                        |
|---------------|----------------------------------------------------------------------|
| **Blastoise** | `DLEN256 VLEN512;   FP; VRF p0rw,p1rw bank1; LSU bank8  beatbyte 8`  |
| **Machamp**   | `DLEN512 VLEN1K ; NOFP; VRF p0r,p1w   bank2; LSU bank8  beatbyte 16` |
| **Sandslash** | `DLEN1K  VLEN4K ; NOFP; VRF p0rw      bank4; LSU bank16 beatbyte 16` |
| **Alakazam**  | `DLEN2K  VLEN16K; NOFP; VRF p0rw      bank8; LSU bank8  beatbyte 64` |

#### Run Testcases

To run testcase on IP emulator, use the following script:
```shell
$ nix develop -c t1-helper ipemu -c <config-name> -C <case-name>
```
wheres
- `<config-name>` is the configuration name;
- `<case-name>` is the name of a testcase, you can resolve runnable test cases by command: `make list-testcases`;

For example:
```shell
$ nix develop -c t1-helper ipemu --config blastoise -C intrinsic.linear_normalization
```

To get waveform, add `--trace` at the end of the above command:

```console
$ nix develop -c t1-helper ipemu --config blastoise -C intrinsic.linear_normalization --trace
```

The `t1-helper` is a emulator wrapper that provides various command-line options for different use cases.
To get all available options, run:

```console
$ nix develop -c t1-helper --help
```

#### Export RTL Properties

```shell
$ nix run .#t1.<config-name>.ip.omreader <key> # export the contents of the specified key
$ nix run .#t1.<config-name>.ip.emu-omreader <key> # export the contents of the specified key with emulation support
```

To dump all available keys and preview their contents:

```shell
$ nix run .#t1.<config-name>.ip.omreader -- run --dump-methods
$ nix run .#t1.<config-name>.ip.emu-omreader -- run --dump-methods
```

<details>
  <summary>Schema</summary>

  ##### `vlen` : Integer

  ##### `dlen` : Integer

  ##### `extensionsJson` : Json

  | Field                           | Type   |
  |---------------------------------|--------|
  | `[*]`                           | string |

  ##### `march` : String

  ##### `decoderInstructionsJson` | `decoderInstructionsJsonPretty` : Json

  | Field                           | Type   |
  |---------------------------------|--------|
  | `[*]`                           | array  |
  | `[*].attributes`                | object |
  | `[*].attributes[*]`             | array  |
  | `[*].attributes[*].description` | string |
  | `[*].attributes[*].identifier`  | string |
  | `[*].attributes[*].value`       | string |

</details>

### Development

#### Developing Elaborator (Chisel-only)
```shell
$ nix develop .#t1.elaborator  # bring up scala environment, circt tools, and create submodules

$ nix develop .#t1.elaborator.editable  # or if you want submodules editable

$ mill -i elaborator  # build and run elaborator
```

#### Developing Emulator
```shell
$ nix develop .#t1.<config-name>.ip.emu  # replace <config-name> with your configuration name
$ cd ipemu/csrc
$ cmake -B build -GNinja -DCMAKE_BUILD_TYPE=Debug
$ cmake --build build
$ cd ..; ./scripts/run-test.py verilate --emulator-path=ipemu/csrc/build/emulator conv-mlir
```

If using clion,
```shell
$ nix develop .#t1.<config-name>.ip.emu -c clion ipemu/csrc
```

#### Rocket emulator

Rocket emulator contains multiple build phrase: RTL -> MLIR Bytecode ->
system verilog -> verilated C sources -> Rust emulator.

Most of the developer doesn't need to care about MLIR, system verilog and verilate detail.
To develop the Rocket-chip RTL, run:

```bash
# This command provide a environment that contains mill, circt, espresso... development tools.
nix develop '.#t1.elaborator'
```

> Metals LSP users are recommended to switch to mill-bsp mode instead of the default bloop mode.

To elaborate the RTLs, run mill or use the nix chroot:

```bash
# for development
mill -i elaborator.runMain org.chipsalliance.t1.elaborator.Main
# for clean build
nix build .#t1.rocketv-mlirbc
```

To develop the emulator, use the below nix environment:

```bash
nix develop .#t1.rocketv-emu.driver.devShell
```

This will setup the verilated C src in environment, download rust-analyzer.

```bash
cd rocketemu/driver
cargo build --release
```

#### Developing Testcases
The `tests/` contains the testcases. There are four types of testcases:

- asm
- intrinsic
- mlir
- codegen
- perf

To add new testcases for asm/intrinsic/mlir, create a new directory with `default.nix` and source files.
Refer to the existing code for more information on how to write the nix file.

To add new testcases for codegen type cases, add new entry in `codegen/*.txt`, then our nix macro will automatically populate new testcases to build.

To view what is available to ran, use the `nix search` sub command:

```console
$ nix search .#t1 <regexp>
```

For example,
```console
$ nix search .#t1 asm
* legacyPackages.x86_64-linux.t1.<config-name>.cases.asm.fpsmoke
  Test case 'fpsmoke', written in assembly.

* legacyPackages.x86_64-linux.t1.<config-name>.cases.asm.memcpy
  Test case 'memcpy', written in assembly.

* legacyPackages.x86_64-linux.t1.<config-name>.cases.asm.mmm
  Test case 'mmm', written in assembly.

* legacyPackages.x86_64-linux.t1.<config-name>.cases.asm.smoke
  Test case 'smoke', written in assembly.

* legacyPackages.x86_64-linux.t1.<config-name>.cases.asm.strlen
  Test case 'strlen', written in assembly.

* legacyPackages.x86_64-linux.t1.<config-name>.cases.asm.utf8-count
  Test case 'utf8-count', written in assembly.

# Then ignore the `legacyPackage.x86_64-linux` attribute, build the testcase like below:
$ nix build .#t1.<config-name>.cases.asm.smoke
```

To develop a specific testcases, enter the development shell:

```shell
# nix develop .#t1.<config-name>.cases.<type>.<name>
#
# For example:

$ nix develop .#t1.<config-name>.cases.asm.smoke
```

Build tests:

```shell
# build a single test
$ nix build .#t1.<config-name>.cases.intrinsic.matmul -L
$ ls -al ./result

# build all tests
$ nix build .#t1.<config-name>.cases.all --max-jobs $(nproc)
$ ls -al ./result
```

> [!TIP]
> All the `mk*Case` expression are defined in `./nix/t1/default.nix`.

### Bump Dependencies
Bump nixpkgs:
```shell
$ nix flake update
```

Bump chisel submodule versions:
```shell
$ cd nix/t1
$ nix run '.#nvfetcher'
```

## License
Copyright © 2022-2023, Jiuyang Liu. Released under the Apache-2.0 License.
