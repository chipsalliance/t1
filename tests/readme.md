## How tests are organised

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
If you are going to build all the tests manually, you will need to set the following environment variable:

- `CODEGEN_BIN_PATH`: Path to the `single` binary in riscv-vector-tests.
- `CODEGEN_INC_PATH`: A list of header include paths. You may need to set it as `export CODEGEN_INC_PATH="$codegen/macro/sequencer-vector $codegen/env/sequencer-vector`.
- `CODEGEN_CFG_PATH`: Path to the `configs` directory in riscv-vector-tests.

You can run `nix develop .#testcase-bootstrap` to have this env set up for you.

## How to resolve all the tests

```bash
mill resolve _[_].elf

# Get asm test only
mill resolve asm[_].elf
```

## How to run the test

Test cases are run by an emulator specified in the `build.sc` file which located at the project root.
`mill` will create a bunch of `verilatorEmulator[__]` objects by reading test file in env `TEST_CASE_DIR`.
The `TEST_CASE_DIR` must point to a directory with the following layout:

```text
$TEST_CASE_DIR/
  configs/
    testName-testModule.json
    ...
  cases/
    testModule/
      testName.elf
    ...
```

There is a script in `.github/scripts/ci.sc` that will help you build all the test cases and generate this directory:

```bash
# You will need ammonite
amm .github/scripts/ci.sc buildAllTestCase . ./tests-out
```

Then set the `TEST_CASE_DIR` to `$PWD/tests-out` (must be an absolute path), and run `cd .. && mill resolve verilatorEmulator[__]`,
you will get a list of executable test case targets.

All the `verilatorEmulator` objects will be generated in this form: `verilatorEmulator[ $emulatorConfig, $testCase, $runtimeConfig ]`.

The emulator configuration can be found in the `configs/` directory in the project root. Each has its own configuration.
In GitHub CI, we will use `v1024l8b2-test` as the base emulator configuration.

The test case names are taken from the `TEST_CASE_DIR/configs/*.json` with the extension stripped. To make `mill` generate more tests or less tests,
you can modify the content of `$TEST_CASE_DIR`.

The runtime config can be found in `run/` directory in the project root. There is `debug.json` only for now.

So to use the `v1024l8b2-test.json` config for the emulator to run the test config `matmul-mlir.json` with runtime config `debug.json`,
you can type `mill -i verilatorEmulator[v1024l8b2-test,matmul-mlir,debug].run` in your shell.

To reduce all the tedious setup steps, you can use the provided `.#testcase` shell:

```bash
nix develop .#testcase
mill -i verilatorEmulator[v1024l8b2-test,matmul-mlir,debug].run
```

## How to build single test and run it

Assuming that you want to build the mlir/hello.mlir test and run it:

```bash
# First, we build the test case
pushd tests
nix develop .#testcase-bootstrap
mill --no-server caseBuild[hello-mlir].run
exit
popd

# Then run the test
nix develop .#testcase
export TEST_CASE_DIR=$PWD/tests-out
mill --no-server verilatorEmulator[v1024l8b2-test,hello-mlir,debug].run
```

