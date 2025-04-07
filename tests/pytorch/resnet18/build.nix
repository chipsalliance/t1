{
  fetchurl,
  buildBuddyE2ETest,
}:
let
  checkpointFile = "resnet18-f37072fd.pth";
  modelCache = fetchurl {
    url = "https://download.pytorch.org/models/${checkpointFile}";
    hash = "sha256-83By/UfonF6CdiHFuv+nUAgZ94lrus7BYLGhbFYOB+w=";
  };
in
buildBuddyE2ETest {
  caseName = "resnet18";

  optPhase = ''
    mkdir -p pytorchCache/hub/checkpoints/
    cp -v ${modelCache} pytorchCache/hub/checkpoints/${checkpointFile}
    export TORCH_HOME=pytorchCache

    python3 ./resnet18.py --output-dir $PWD

    echo "Lowering forward.mlir"
    buddy-opt forward.mlir -pass-pipeline \
        "builtin.module(func.func(tosa-to-linalg-named, tosa-to-linalg, tosa-to-tensor, tosa-to-arith{use-32-bit}), \
            empty-tensor-to-alloc-tensor, convert-elementwise-to-linalg, arith-bufferize, \
            func.func(linalg-bufferize, tensor-bufferize), func-bufferize, convert-vector-to-llvm)" \
      | buddy-opt -pass-pipeline \
        "builtin.module(func.func(buffer-deallocation-simplification, convert-linalg-to-loops), \
            eliminate-empty-tensors, func.func(llvm-request-c-wrappers), \
            convert-math-to-llvm, convert-math-to-libm, convert-scf-to-cf, \
            convert-arith-to-llvm{index-bitwidth=32}, convert-func-to-llvm{index-bitwidth=32}, \
            expand-strided-metadata, finalize-memref-to-llvm{index-bitwidth=32}, \
            convert-func-to-llvm{index-bitwidth=32}, reconcile-unrealized-casts)" \
      > forward-lowered.mlir

    echo "Lowering subgraph0.mlir"
    buddy-opt subgraph0.mlir -pass-pipeline \
        "builtin.module(func.func(tosa-to-linalg-named, tosa-to-arith{use-32-bit}, tosa-to-linalg, tosa-to-tensor))" \
      | buddy-opt \
          --convert-elementwise-to-linalg \
          --eliminate-empty-tensors \
          --empty-tensor-to-alloc-tensor \
          --one-shot-bufferize \
          --func-bufferize-dynamic-offset \
          --tensor-bufferize \
          --arith-bufferize \
          --buffer-deallocation \
          --finalizing-bufferize \
          --convert-linalg-to-affine-loops \
          --affine-loop-fusion \
          --lower-affine \
          --expand-strided-metadata \
          --convert-vector-to-scf \
          --convert-scf-to-cf \
          --memref-expand \
          --llvm-request-c-wrappers \
          --lower-vector-exp \
          --lower-rvv=rv32 \
          --convert-vector-to-llvm \
          --convert-math-to-llvm \
          --convert-arith-to-llvm=index-bitwidth=32 \
          --convert-index-to-llvm=index-bitwidth=32 \
          --convert-func-to-llvm=index-bitwidth=32 \
          --finalize-memref-to-llvm=index-bitwidth=32 \
          --reconcile-unrealized-casts \
      > subgraph0-lowered.mlir

    echo "Compiling memrefCopy library"
    $CXX -nostdlib -c ${../lib/MemrefCopy.cc} -o memrefCopy.o

    llcArtifacts+=(
      memrefCopy.o
    )

    optArtifacts+=(
      "forward-lowered.mlir"
      "subgraph0-lowered.mlir"
    )

    mkdir -p "$out"/share
    cp -v ''${optArtifacts[*]} "$out"/share/
  '';
}
