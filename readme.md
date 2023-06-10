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

## Environment setup

### Run IP emulators
The IP emulators are designed to emulate the vector IP. Spike is used as the scalar core integrating with verilated vector IP and use difftest for IP-level verification, it records events from VRF and LSU to perform online difftest.
You can find different cases under `tests/cases` folder, they are dynamically detected found by the `mill` build system, please refer to the `build.sc` and mill documentation for more informations.

#### Nix setup
We use nix flake to setup test environment. If you have not installed nix, install it following the [guide](https://nixos.org/manual/nix/stable/installation/installing-binary.html), and enable flake following the [wiki](https://nixos.wiki/wiki/Flakes#Enable_flakes). For example:

Install with package manager e.g. on ArchLinux:
```shell
pacman -S nix
```
or with installation script:
```
sh <(curl -L https://nixos.org/nix/install)
```

Add flake to configurations:
```shell
echo "experimental-features = nix-command flakes" >> ~/.config/nix/nix.conf
```

Enable nix-daemon systemd service (if your package manager has not enabled it automatically):
```shell
systemctl start nix-daemon.service
```

After nix is installed, run `nix develop` to enter the development shell, all environment variables and dependencies is included. If you want a pure environment without system packages, use `env -i nix develop` instead.

#### Test
In nix development shell, run with `mill resolve tests.__` to see all tests.
For example use `mill -i 'tests.run[smoke.asm]'` to run a simple test.

## Patches
<!-- BEGIN-PATCH -->
<!-- END-PATCH -->

## License
Copyright © 2022-2023, Jiuyang Liu. Released under the Apache-2.0 License.

