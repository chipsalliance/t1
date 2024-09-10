## Organizational Summary

`difftest` is a verification framework designed to separate the driver and difftest phases. The framework supports testing for two verification objects: `t1` and `t1rocket`. The basic structure and functionality are as follows:

### Directory Structure

1. **Top-Level Directory**
   - `Cargo.lock` and `Cargo.toml`: For project management and dependency configuration.
   - `default.nix`: Nix configuration file for building and managing the project environment.
   - `doc/`: Documentation directory containing information about the project organization and structure.

2. **DPI Driver Directories**
   - `dpi_common/`: Contains shared library code, which are used across different verification objects.
   - `dpi_t1/` and `dpi_t1rocket/`: Contain the TestBench code for `t1` and `t1rocket`, respectively. Each directory includes source files providing the DPI library linked by emulator(vcs or verilator), these DPIs will be called by corresponding Testbench.

3. **Difftest Directories**
   - `offline_t1/` and `offline_t1rocket/`: Correspond to the verification projects for `t1` and `t1rocket`, respectively. These directories include the difftest code files, used for the difftest verification framework.
   - `spike_interfaces/`: Contains C++ code files for interface definitions.
   - `spike_rs/`: Source files include `lib.rs`, `runner.rs`, and `spike_event.rs`, which provide the methods and tools needed during the verification phase.

### Workflow

1. **TestBench Generation**
   - For each verification object (`t1` and `t1rocket`), corresponding TestBench code is used with emulator to generate the static library.

2. **Driving and Verification**
   - The generated static library is driven by the Rust code in `spike_rs`.
   - During the verification phase, interfaces provided by `spike_rs`  generate the architectural information.

3. **Testing and Validation**
   - Code in the `offline_t1` and `offline_t1rocket` directories carries out the actual offline difftest verification work, using `difftest.rs` and other test code to test the generated architectural information.
