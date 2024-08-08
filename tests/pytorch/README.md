## How to add tests

To create a new PyTorch test, you can follow the below instruction.

Assuming that the new PyTorch test have project name call `demo`, let's create the test skeleton:

```bash
cd tests/pytorch
mkdir -p demo
cd demo
touch demo.c demo.py config.nix
```

Developers should put their PyTorch implementation into "<project-name>.py" file.
For each PyTorch tests, developers must write the MLIR model to "forward.mlir" file.

```python
# demo.py
#...
with open("forward.mlir", "w") as mlir_module:
    print(graph._imported_module, file = mlir_module)
```

For each PyTorch tests, developers should call the MLIR model from "<project-name>.c" file.
In our case, here is an example "demo.c" file:

```c
// 1. Include the MemRef wrapper
#include "memref.h"

// 2. Create corresponding MemRef struct with data type `float` and one dimension.
NEW_MEMREF(float, 1);

// 3. Declare the MLIR model interface
extern void _mlir_ciface_forward(struct MemRef_float_dim1 *output,
                                 struct MemRef_float_dim1 *arg1,
                                 struct MemRef_float_dim1 *arg2);

// 4. Create example data array. The ".vdata" attribute will help emulator load the data into correct memory.
__attribute((section(".vdata"))) float input_float_0[512] = {1, 2, 3};
struct MemRef_float_dim1 input1 = {
    .allocatedPtr = input_float_0,
    .alignedPtr = input_float_0,
    .offset = 0,
    .sizes = {512},
    .strides = {1},
};

// 5. Declare the main entry. In t1 all tests entry should be `int test()` instead of main().
int test() {
  _mlir_ciface_forward(&output, &input1, &input2);
  return 0;
}
```

After PyTorch model and the C entry is correctly created, developers should declare a "config.nix"
file to indicate our build system to find and build the test case:

```nix
{
  # Tell our build system to include the memref.h header.
  # Developer could add extra headers here.
  includes = [
    ../memref.h
  ];

  # Tell the build system to run buddy-opt with three phrase, with arguments to run in each phrase
  buddyOptArgs = [
    [
      "--pass-pipeline"
      "builtin.module(func.func(tosa-to-linalg-named, tosa-to-linalg, tosa-to-tensor, tosa-to-arith), empty-tensor-to-alloc-tensor, convert-elementwise-to-linalg, arith-bufferize, func.func(linalg-bufferize, tensor-bufferize), func-bufferize)"
    ]
    [
      "--pass-pipeline"
      "builtin.module(func.func(buffer-deallocation-simplification, convert-linalg-to-loops), eliminate-empty-tensors, func.func(llvm-request-c-wrappers))"
    ]
    [
      "--lower-affine"
      "--convert-math-to-llvm"
      "--convert-math-to-libm"
      "--convert-scf-to-cf"
      "--convert-arith-to-llvm"
      "--expand-strided-metadata"
      "--finalize-memref-to-llvm"
      "--lower-vector-exp"
      "--lower-rvv=rv32"
      "--convert-vector-to-llvm"
      "--convert-func-to-llvm"
      "--reconcile-unrealized-casts"
    ]
  ];
}
```

Our build system accept the below data layout for the "config.nix" file:

```text
Set {
    buddyOptArgs: Array<Array<String>>,

    includes: Optional<Array<String>>,
    pythonArgs: Optional<Array<String>>,
    buddyTranslateArgs: Optional<Array<String>>,
    buddyLLCArgs: Optional<Array<String>>,
}
```

After the project have been implemented, developers can run the below commands to build and test the ELF:

```bash
git add .
nix build '.#t1.blastoise.ip.cases.pytorch.demo' -L
ls ./result/bin/pytorch-demo.elf

# To start the emulator and get waveform, run:
nix build '.#t1.blastoise.ip.cases.pytorch.demo.emu-result.with-trace' -L
```

## FAQ

* How to debug the PyTorch code

```bash
nix run '.#buddy-mlir-pyenv' -- demo.py
```

* How to run buddy compiler tools manually

```bash
nix develop '.#t1.blastoise.ip.cases.pytorch.demo' -L
cd $(mktemp -d -t 'pytorch-debug-XXX')
pwd

# Unpack sources
unpackPhase
# Check commands:
echo -e "$buildPhase"
# Run build
bash -c "$buildPhase"
```
