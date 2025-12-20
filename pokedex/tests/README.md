# Pokedex Test Suite Technical Manual

This manual documents the architecture, usage, and configuration of the Pokedex
test suite. This project validates the Pokedex RISC-V simulator by running
differential tests against the Spike simulator.

## 1. Quick Start (Nix)

The recommended way to run tests is using **Nix**. It handles toolchains,
dependencies, and configuration automatically.

### Running Tests
To run the full test suite via Nix:

```bash
nix build '.#pokedex.zve32x.tests.run' -L
```

### Development Environment
For interactive development (compiling individual tests, debugging):

1.  **Enter the Shell**:
    ```bash
    nix develop '.#pokedex.zve32x.tests.env'
    ```
    This loads `meson`, `ninja`, `clang` (cross-compiler), and pre-configures environment variables.

2.  **Configure Build**:
    ```bash
    meson setup build $MESON_FLAGS
    ```
    *`$MESON_FLAGS` is automatically provided by the Nix shell.*

3.  **Run Tests**:
    ```bash
    meson test -C build
    ```

## 2. Configuration Tables

### Meson Build Options (`meson.options`)

These options configure the build target and test generation.

| Option                | Type      | Default                    | Description                                                                 |
| :---                  | :---      | :---                       | :---                                                                        |
| `xlen`                | integer   | `32`                       | XLEN (Register width).                                                      |
| `vlen`                | integer   | `128`                      | VLEN (Vector register length).                                              |
| `march`               | string    | `rv32imafc_zvl256b_zve32f` | RISC-V Architecture string.                                                 |
| `abi`                 | string    | `ilp32f`                   | ABI string for compilation.                                                 |
| `zve32f`              | feature   | `enabled`                  | Enable vector floating-point support.                                       |
| `scalar_fp`           | feature   | `enabled`                  | Enable scalar floating-point support.                                       |
| `riscv_tests_src`     | string    | *Empty*                    | **Required**. Absolute path to `riscv-software-src/riscv-tests` source.     |
| `codegen_install_dir` | string    | *Empty*                    | **Required**. Absolute path to vector test generator (`riscv-vector-tests`).|
| `with_tests`          | feature   | `disabled`                 | Enable test execution targets (requires `spike` and `pokedex`).             |
| `prebuilt_case_dir`   | string    | *Empty*                    | Path to directory containing pre-compiled ELFs (skips compilation).         |

### Environment Variables (Runtime)

The `difftest.py` runner uses these variables during execution.

| Variable         | Description                                                                 |
| :---             | :---                                                                        |
| `MARCH`          | Architecture string (passed to Spike via `--isa`).                          |
| `SPIKE`          | Path to the `spike` simulator executable.                                   |
| `POKEDEX`        | Path to the `pokedex` simulator executable.                                 |
| `POKEDEX_CONFIG` | Path to the `pokedex-config.kdl` hardware configuration file.               |

## 3. Adding New Tests

### 3.1 Adding a Test to an Existing Suite
This is the simplest way to add a test case.

**Smoke Tests (`smoke/`, `smoke_v/`)**:
1.  Create your C or Assembly file in `smoke/src/` (e.g., `my_test.c`).
2.  Edit `smoke/meson.build`. Add your test to the `srcs` dictionary:
    ```python
    srcs = {
      'addi': 'src/addi.c',
      'mul': 'src/mul.S',
      'my_test': 'src/my_test.c' # Add this line
    }
    ```
3.  Rebuild: `meson compile -C build`.

**External Standard Tests**:
*   **RISC-V ISA Tests**: Located in the external `riscv-tests` repository. To
    enable one, add its filename to `riscv-tests/case_list.txt`.
*   **Vector Tests**: Located in `riscv-vector-tests`. Generated from TOML
    configs in the external `codegen` tool. To enable one, add its name to
    `riscv-vector-tests/case_list.txt`.

### 3.2 Creating a New Test Suite
If you need a completely new category of tests (e.g., `new_suites/`), follow
these steps.

#### Step 1: Create Directory and Source
Create a directory `new_suites/` and add your test source (e.g., `test.S`).

#### Step 2: Create `meson.build`
Create `new_suites/meson.build`. You must define the build logic manually. Use
the template below.

**Crucial Logic Explanation**:
*   **Flags**: You generally **must** link against the project's bare-metal
    runtime. This means including `stub_linker_script` and `stub_main`.
*   **Variables**: Variables like `stub_linker_script`, `stub_main`, `clang`,
    `objdump`, `spike`, `pokedex` are defined in the root `meson.build` and are
    available here.

**Template (`new_suites/meson.build`)**:
```meson
# 1. Define Compilation Flags for Bare Metal
cflags = [
  '-march=' + get_option('march'),
  '-mabi=' + get_option('abi'),
  '-T' + stub_linker_script, # CRITICAL: Uses project memory map
  '-O0',
  '-static',
  '-mcmodel=medany',
  '-fvisibility=hidden',
  '-nostdlib',
  '-nostartfiles',
  '-fno-PIC',
]

# 2. Define Sources
srcs = {'my_custom_test': 'test.S'}

# 3. Build Loop
suite = 'new_suites'
foreach case_name, case_src : srcs
  # A. Compile ELF (Links with stub_main for entry/exit)
  elf = custom_target(
    suite + '.' + case_name,
    input: [case_src, stub_main], # CRITICAL: Includes startup code
    output: case_name + '.elf',
    command: [clang] + cflags + ['@INPUT@', '-o', '@OUTPUT@'],
    build_by_default: true,
    install: true,
    install_dir: suite / 'bin',
  )

  # B. Define Test (if enabled)
  if with_tests
    # Run the difftest runner
    diff_result = custom_target(
      suite + '.' + case_name + '_diff',
      depends: [spike, pokedex],
      input: elf,
      output: [case_name + '_spike.log', case_name + '_pokedex.jsonl', case_name + '_result.json'],
      env: [
        'MARCH=' + march,
        'SPIKE=' + spike.full_path(),
        'POKEDEX=' + pokedex.full_path(),
        'POKEDEX_CONFIG=' + pokedex_config,
      ],
      command: [
        difftest_runner,
        '--elf', '@INPUT@',
        '--spike-log', '@OUTPUT0@',
        '--pokedex-log', '@OUTPUT1@',
        '--diff-result', '@OUTPUT2@',
      ],
      install: true,
      install_dir: suite / case_name,
    )

    # Register with Meson test runner
    test(
      case_name,
      difftest_runner,
      args: ['--check', '--diff-result', diff_result[2]],
      depends: [diff_result],
      suite: suite,
    )
  endif
endforeach
```

#### Step 3: Register in Root
Open the root `meson.build` file and add your new subdirectory:

```python
subdir('smoke')
subdir('riscv-tests')
subdir('new_suites') # Add this line
```

## 4. Technical Implementation Details

### 4.1 Build System Architecture
The project uses **Meson** to orchestrate a multi-stage build pipeline:

1.  **Generation (Vector only)**:
    *   Input: `case_list.txt` + TOML configs.
    *   Tool: `single` (from `codegen` dependency).
    *   Output: RISC-V Assembly (`.S`).
2.  **Compilation**:
    *   Input: `.S` or `.c` files + `compile-stubs/main.S`.
    *   Tool: `riscv32-none-elf-clang`.
    *   Flags: `-nostdlib`, `-static`, `-T compile-stubs/script.ld`.
    *   Output: ELF binaries (`.elf`).
3.  **Disassembly**:
    *   Tool: `riscv32-none-elf-objdump`.
    *   Output: `.objdump` text files for debugging.
4.  **Test Definition**:
    *   Meson defines a `test()` target for each case.
    *   Wrapper: `difftest.py`.

### 4.2 Differential Testing (The `difftest.py` Runner)
Verification is performed by comparing execution traces between **Spike** (Golden Model) and **Pokedex** (DUT).

1.  **Execution**:
    *   **Spike**: Runs with `--log-commits` to produce a text log of register writes.
    *   **Pokedex**: Runs with `run --output-log-path ...` to produce a structured JSON Lines (`.jsonl`) log.
2.  **Comparison**:
    *   The runner invokes `pokedex difftest`.
    *   Inputs: Spike log, Pokedex log.
    *   Output: `diff_result.json`.
3.  **Assertion**:
    *   The runner checks `result["is_same"]` in the JSON output.
    *   If `false`, the test fails and prints divergence details.

### 4.3 Runtime Environment (Stubs)
All tests run in a bare-metal environment defined in `compile-stubs/`.

#### Entry Points (`_start`)
There are two distinct entry point definitions, both residing in the `.text.init` section:

1.  **Smoke Tests (`compile-stubs/main.S`)**:
    *   Used by `smoke` and `smoke_v` suites.
    *   Explicitly defines `_start`.
    *   Initializes registers, enables Vector Extension (`mstatus.VS`), sets up trap handlers, and calls the user-defined `test` function.

2.  **Standard & Vector Tests (`riscv_test.h`)**:
    *   Used by `riscv-tests` and `riscv-vector-tests`.
    *   The `RVTEST_CODE_BEGIN` macro (from `compile-stubs/include/riscv_test.h`) defines `_start`.
    *   Follows the standard `riscv-tests` convention: performs low-level initialization (XLEN check, trap vector setup) and jumps to the test body defined in the generated assembly.

#### Memory Map (`script.ld` & `pokedex-config.kdl`)
*   **SRAM**: `0x80000000` (512MB). Code and Data.
*   **MMIO**: `0x40000000` (4KB). Used for simulation control.
*   **Exit Mechanism**: Writing to `0x40000004` (mapped to "exit" in KDL) terminates the Pokedex simulation.

### 4.4 Nix Integration & Dependencies
The `default.nix` and `nix/` directory provide a reproducible environment.

*   **External Repositories**:
    *   `riscv-tests` (Source): Used for `riscv-tests/` suite.
    *   `riscv-vector-tests` (Codegen): Used for `riscv-vector-tests/` suite.
    *   **Note**: In the Nix environment (`nix develop`), these are
        automatically downloaded and their paths are passed to Meson via
        `$MESON_FLAGS` (mapped to `-Driscv_tests_src=...` and
        `-Dcodegen_install_dir=...`). If you are working outside Nix, you must
        clone these repos manually and pass the paths to `meson setup`.
*   **Derivation**: `nix/run.nix` encapsulates the Meson build.
*   **Prebuilt Support**: `nix/prebuilt-cases.nix` creates a derivation that
    *only* builds the ELFs. This allows the test runner (`run.nix`) to simply
    consume existing binaries via the `prebuilt_case_dir` option, saving
    compilation time during repeated test runs.
