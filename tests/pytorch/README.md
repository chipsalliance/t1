## How to add tests

To create a new PyTorch test, you can follow the below instruction.

Assuming that the new PyTorch test have project name call `demo`, let's create the test skeleton:

```bash
cd tests/pytorch
mkdir -p demo
cd demo
touch demo.cc demo.py build.nix
```

Developers should put their PyTorch implementation into "<project-name>.py" file.

```python
# demo.py
#...
with open("forward.mlir", "w") as mlir_module:
    print(graph._imported_module, file = mlir_module)
```

For each PyTorch tests, developers should call the MLIR model from "<project-name>.c" file.
In our case, here is an example "demo.c" file:

```c
// 1. include the memref C++ wrapper.
#include "memref.hpp"

// 2. Declare the MLIR C interface, the argument layout can be guess from the generated MLIR model.
extern "C" void _mlir_ciface_forward(MemRef<float, 1> *output,
                                     MemRef<float, 1> *arg1,
                                     MemRef<float, 1> *arg2);

// 3. Declare the data sizes, here we use a vector that is one-dimension, with length 512.
static const int32_t sizes[1] = {512};

// 4. Declare a static data and add ".vdata" annotation. This can indicate the emulator to put these data to correct memory.
__attribute((section(".vdata"))) float input_float_1[512] = {1, 2, 3};

// 5. Declare a one dimension MemRef with float data type.
MemRef<float, 1> input1(input_float_1, sizes);

// 6. Mark the test function as extern "C", so that the linker can link it with our main function.
extern "C" int test() {
  // call _mlir_ciface_forward(...)
  return 0;
}
```

After PyTorch model and the C entry is correctly created, developers should declare a "build.nix"
file to indicate our build system to find and build the test case:

```nix
{ buildBuddyE2ETest }:
buildBuddyE2ETest {
  caseName = "demo";

  optPhase = ''
    echo "Lowering MLIR"
    python ./demo.py \
    | buddy-opt --pass-pipeline "builtin.module(func.func(tosa-to-linalg-named, tosa-to-linalg, tosa-to-tensor, tosa-to-arith),\
        empty-tensor-to-alloc-tensor, convert-elementwise-to-linalg, arith-bufferize, \
        func.func(linalg-bufferize, tensor-bufferize), func-bufferize)" \
    | buddy-opt --pass-pipeline "builtin.module(func.func(buffer-deallocation-simplification, convert-linalg-to-loops), \
        eliminate-empty-tensors, func.func(llvm-request-c-wrappers))" \
    | buddy-opt --lower-affine \
        --convert-math-to-llvm \
        --convert-math-to-libm \
        --convert-scf-to-cf \
        --convert-arith-to-llvm \
        --expand-strided-metadata \
        --finalize-memref-to-llvm \
        --lower-vector-exp \
        --lower-rvv=rv32 \
        --convert-vector-to-llvm \
        --convert-func-to-llvm \
        --reconcile-unrealized-casts \
        -o forward-lowered.mlir

    optArtifacts+=(
      "forward-lowered.mlir"
    )
  '';
}
```

Here you can think `optPhase` as a bash function. Developers can write their own pass in this function.
Each `optPhase` should modify the `optArtifacts` array, to indicate our build system about the final output.

The `caseName` and `optPhase` attribute is always required.
We also offer the below attribute for you to override:

* `translatePhase`: By default, run `buddy-translate --buddy-to-llvmir` for each file in `optArtifacts` array,
  add output into `translateArtifacts` array.
* `llcPhase`: By default run `buddy-llc` for each file in `translateArtifacts` array, add output to `llcArtifacts` array.
* `linkPhase`: By default link all the .o object file present in `llcArtifacts` array and the `caseName.cc` C++ file.

> Developer can add other `stdenv.mkDerivation` attribute to override if they know the risks.

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
nix run '.#buddy-mlir.pyenv' -- demo.py
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
