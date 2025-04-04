{
  fetchurl,
  buildBuddyE2ETest,
}:
let
  checkpointFile = "mobilenet_v3_small-047dcff4.pth";
  modelCache = fetchurl {
    url = "https://download.pytorch.org/models/${checkpointFile}";
    hash = "sha256-BH3P9K3e+G6lvC7/E8lhTcEfR6sRYNCnGiXn25lPTh8=";
  };
in
buildBuddyE2ETest {
  caseName = "mobilenet";

  optPhase = ''
    mkdir -p pytorchCache/hub/checkpoints/
    cp -v ${modelCache} pytorchCache/hub/checkpoints/${checkpointFile}
    export TORCH_HOME=pytorchCache
    python ./mobilenet.py

    echo "Lowering forward.mlir"
    buddy-opt forward.mlir -pass-pipeline \
        "builtin.module(func.func(tosa-to-linalg-named, tosa-to-linalg, tosa-to-tensor, tosa-to-arith), \
              empty-tensor-to-alloc-tensor, convert-elementwise-to-linalg, arith-bufferize, \
              func.func(linalg-bufferize, tensor-bufferize), func-bufferize)" \
      | buddy-opt -pass-pipeline \
        "builtin.module(func.func(buffer-deallocation-simplification, convert-linalg-to-loops), \
              eliminate-empty-tensors, func.func(llvm-request-c-wrappers), \
              convert-math-to-llvm, convert-math-to-libm, convert-scf-to-cf, \
              convert-arith-to-llvm, expand-strided-metadata, finalize-memref-to-llvm, \
              convert-func-to-llvm, reconcile-unrealized-casts)" \
      > forward-lowered.mlir

    echo "Lowering subgraphs[0]"
    buddy-opt subgraphs0.mlir -pass-pipeline \
        "builtin.module(func.func(tosa-to-linalg-named, tosa-to-arith, tosa-to-linalg, tosa-to-tensor))" \
      | buddy-opt \
          --convert-elementwise-to-linalg \
          --func-bufferize-dynamic-offset \
          --arith-bufferize \
          --func-bufferize \
          --tensor-bufferize \
          --linalg-bufferize \
          --finalizing-bufferize \
          --batchmatmul-optimize \
          --convert-linalg-to-affine-loops \
          --lower-affine \
          --convert-vector-to-scf \
          --convert-scf-to-cf \
          --llvm-request-c-wrappers \
          --lower-vector-exp \
          --lower-rvv=rv32 \
          --convert-vector-to-llvm \
          --convert-math-to-llvm \
          --convert-math-to-libm \
          --convert-arith-to-llvm \
          --convert-func-to-llvm \
          --expand-strided-metadata \
          --finalize-memref-to-llvm \
          --reconcile-unrealized-casts \
      > subgraphs0-lowered.mlir

    echo "Compiling memrefCopy library"
    $CXX -nostdlib -c ${../lib/MemrefCopy.cc} -o memrefCopy.o
    llcArtifacts+=(
      memrefCopy.o
    )

    optArtifacts+=(
      "forward-lowered.mlir"
      "subgraphs0-lowered.mlir"
    )
  '';
}
