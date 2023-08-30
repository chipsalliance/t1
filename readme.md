# T1 - The RISC-V Long Vector Machine Hardware Generator

Welcome to the T1 project, a RISC-V Long Vector Machine Hardware Generator using the Chisel hardware construction language. It is fully compliant with the RISC-V Vector Spec 1.0. The architecture is inspired by the Cray X1 vector machine.

## Architecture Highlights:

- Support any RISC-V scalar cores without MMU;
- VLEN/DLEN can be configured to as much as you wish;
  - Support multiple lanes;
  - Support different SRAMs and VRF can be banked;
  - Multiple Vector Function Unit (VFU) per lane;
  - Lane instruction slots to/from VFUs matrix;
- Ring bus based lane interconnection;
- Vector **Load to VFU to VFU to VFU to VFU to Store** chaining(up to 4 VFU for now.)
- Fully configurable to hit the high throughput utilization rate;

<!--https://viewer.diagrams.net/?tags=%7B%7D&highlight=0000ff&edit=_blank&layers=1&nav=1&title=pipe.drawio#R7V1bl5s2EP4tffBjckAgLo%2FZJE3bpG3SpNnkKYca2abBlgN419tfX2FLNmgExruA2MXpOV0jSwI0V30zI0%2Bsl8vtmyRYL36nIYknyAi3E%2BvVBCHk%2BT77k7fc7VtM08L7lnkShbzt2PAx%2Bo%2FwRoO3bqKQpKWOGaVxFq3LjVO6WpFpVmoLkoTelrvNaFy%2B6zqYE9DwcRrEsPU6CrPFvtVD7rH9FxLNF%2BLOpsPfeBmIzvxN0kUQ0ttCk%2FV6Yr1MKM32n5bblyTOV0%2Bsy%2FWvd9fxu%2B%2FOm98%2BpD%2BCv6%2Fefvrj87P9ZD%2BfM%2BTwCglZZfeeGqHb65vrT16W3E3xzP82%2F7xY8iHGTRBv%2BHrxd83uxAKSVfgipwO7WtEVa7wKg3RB8llNdrHIljH%2FmLe%2FD7KMJKtdCzIs1ppmCf1%2BWHu2alcJ3azC3QQGu2r4cnwRSFgiN3%2FVN4QuCXsx1uH2SGTM325RoK9oS0gcZNFNmUkCzmvzw3SHO7ynEXs8ZHDBMD0%2BDxcLS7C7mCKlm2RK%2BKgiVaSJsHFioixI5iQDE7EPhdc%2BNu2IfgYDmIAB0iyXKCSeq8AJGdlmZYrvafuSxjQ5sscsimOpKYijec4TU0ZjwtqvbkiSRUxIX%2FAvllEY5re5ul1EGfm4Dqb5PW%2BZTgL8MqOr7CN%2FqKaSId6S3ZVsa9mHf2tjiSxCCRTYy1Gwl2VUc1KJdOfSCV0EtRVBxbglQQUTdSyoVqWgmhdBLZDF0SyozkVQWxFU325JUMFEHQuqWymokBNGLKg%2B1iyo3kVQWxFU89DwUEmFM3Usqn6lqFoXUS3Sxdcsq6YLZZNJg1gJmmQLOqerIH59bJVW7tjnHaVrTsJ%2FSZbdccwi2GS0TGCyjbIvfHj%2B%2BWv%2B%2BTnmV6%2B2ha9e3YmLFXvwL8eO%2BeVXMV9%2BcRy2uxLjzqPlXsLqFoxTYi9AdR1xQ%2BXSWGs8bJujh9BHorklqhknqJYyWY1Wc3ZlK9X9eVQ9TSyjbWKpFbPl4ZICsAWXnNDwzFgGd4Vu67xDWncfR3mfKoMB%2BrvGif64rj%2F7sH%2FidhGVBphamWNVar%2FAnV2peSRvnRQYx8Eit63mk%2FT1hz8Q%2BrxI776%2F%2FfI1uX52%2B0WgkdqE%2FyzZb1tjN1UCqKk72I%2FGFs8tO1F%2FkflQEcSuBOqA3dYIVL9uE3RjRyRQqKlVHZhAOXqJViLZkYJDI1rrfut5HozwKDwsEbwLjwIBFbsM0u81XoWpz6t45paVoK3yKlCvWtCuWD5jxixMHremm%2FXITJUpQS%2B2Cank90okX7OpQlptFW6q9lrfAT6IapZmB0OvrWpKNOTrtVUVwK1t9GG7MFC%2BsxmFXBPH0Tqt0ok9mC3TlhbH1e27w%2FhExMRmOzZDJcfvFYaq30QMCO%2BscgqwYSMkzyE5ToiN7vC7PebdE2qKIdn2ICySLXmhrt8DPouqASvzG4SsBgnVugot2BlUq05z0RKoaVteUCdiAPjcNYznRuFfOSrhelJ0pSLqfa50OXLGBaqPrsj93T6wDRdr0dcisnuI5n4tfKOO7LbMe47VUFd3xKTncpMrcYdX1tUw9mbX9u%2BGmzyovx%2BvVjrJGa7bNmc8LKEKJqKOJ9rQnGjDAANk18s0jHp5xqh%2BQEfOGoS%2Bj84a5LdBOmu%2BdmcNweTxmqXTGDzwZIhKe%2FAAweDBOlqTnXzOh8l%2Fns68DvUiQhDwzzVJmHqjK2gzdEOBvhTGdyy4fr1iGpYWw3re8uoCwRU5X1aJehZulrvWmsWCScUT5ATLnHn3%2Fx8ex1tYWjPd%2Bb4WNPs%2F7dYsyMeOCmEFtNGujaBFzGmzWYdBlttFZWrC06YRkiyG5UEa9WpwLS2oSrvZ0F0bCt%2Fyn0tAnCHRo2khiy9jHfJELSF67D4Vj1zzZPIIXI8CKpYF94ADWrBsbudgG2QV%2FMPYc1wKxZehWFu30h91eo7FY%2FEacrPvpSeeyTsYXK8lGL%2FV9e9G4m2IQQxuV9MeZ9iPgjO6oTTWDAxrjck31x1okBwiRQ07ittoydrQRWlvGNkXMqAvvIfK%2BLBUTScP6MgxhHlwIZnScA%2FBpps4A6wzCChbzoP3VFGAXqFs4UOWMtfYMuXrGOR%2F2Pds5vw8mfwPNM9P2%2BeWQg%2BOCjXv1ed2HwHse1IpCr%2FnpFJ0hlWFJZ67ICzThKbpUVrSbId3DQ3LdVADPrYUfOx0pndg9JHrnfKKjkrb2E3IZPSpbrDeWo7DhRYvXSTEPhYv%2FXDMWZ9eug2R%2F5tkBrjmhOIL0vX%2BnNdZtM05p3SmjSFJ9ARZM5z%2Fx%2FsV2vf%2FgFpg3zi7fx0Kr5wL4yrOslLh%2FfLxRu2pWE%2Br7J57bErbwus2Fd6B%2BRgqzDvYO%2BQ%2FxmYPLUmkdIegbRjH58SZRasoXYyMPoeUAOGv6C70wajKq7zsZidgN%2BsbunezuMJ5yEGcsVXfYwlj9XSH9zBM0Pu0K2ocFVnkkgXtNkjcrC5vN10E6%2FzjLCZbfljpVeHc0mkcpGk0LRPtCZxC6ppyEdU9czdc78RELeVuiCwK%2BT6d7tickyhWNraKZVeuOVXo3n6BFwd6Mhcp58SRgyiW%2F1ycBXu2oBtgrkaC3lqlow2oOh58rXG140DyJDzZQ3Pqs%2Bbk%2Fr3kVTgQWJe0ezJ27e4pkLl%2BtbuvuypSa%2Bac2xSa8zSf51dVFon4OTxVgm87clJEeUA3ku9ridVo4w2rbd54WFwewra8JPDHhmxgSHgQRYFyUappKAA8ZVXgIUml%2FYWEqT0JmdJkZ7l2yd8TN8fuljTcjC4F3HPLikVVk6WM43dnyeC2ZERBJlG3%2F1gsGcayYTpV4H9iQEeWTO%2BuiDGVZ5V3Robpn%2BCs3dV7kkTs5XP1oZfdWjeO92M3kE1qnzgfRjjGFQM6Om4IRvXIlkw3ykr0QeSR2k4Tw61KJO3OcHsNEKvLb2cpmB78dhaWaXTv384CM3X921l6Du4aBpzlNYazhpHqL0e2TOzVa2c5i%2FTsAb0c5ehBAOw2ibLHthWzVcH5frdi%2FlM4ztFrmsTpeW1L5cMWX29xrWZV2phow3B05VMyTeGgdavpYPbM4yxHOLj5%2BuoR%2FKdQbChQBg267n77QylAZlp2H3WlMK9JiI1wFAZ7jJtcIWIiAdBoO8fNg8g3WLuxZipI%2BUim5d5zZycnJMGZOspIOtyoW5GEoL8skqPPSTJVh3z1G7b2VMfYXUR9Ry1X9gBRe1lJ%2BWSNpL01HEdv6ZC0%2BUA97z5EUO2x7D6wnG5gnzjaBw7o49AGsax1ftfolbzdZ%2Bap8veWIXw22vozS%2F6dKcURji3l%2FrPLhNKsKHHsTRe%2F05DkPf4H-->
### Lanes Basic Profiles:
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

Run `nix develop .#testcase` to enter the test case environment.
In nix development shell, run `mill resolve verilatorEmulator.__` to see all available tests.
For example use `mill -i 'verilatorEmulator[v1024l8b2-test,hello-mlir,debug].run'` to run the `hello.mlir` test with v1024l8b2-test emulator configs.
Read the readme file in tests directory for more details.

## Patches
<!-- BEGIN-PATCH -->
<!-- END-PATCH -->

## License
Copyright © 2022-2023, Jiuyang Liu. Released under the Apache-2.0 License.

