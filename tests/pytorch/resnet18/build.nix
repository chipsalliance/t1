{ fetchurl
, buildBuddyE2ETest
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
        "builtin.module(func.func(tosa-to-linalg-named, tosa-to-linalg, tosa-to-tensor, tosa-to-arith), \
            empty-tensor-to-alloc-tensor, convert-elementwise-to-linalg, arith-bufferize, \
            func.func(linalg-bufferize, tensor-bufferize), func-bufferize)" \
      | buddy-opt \
          -lower-vector-exp \
          -lower-rvv=rv32 \
          -convert-vector-to-llvm \
      | buddy-opt -pass-pipeline \
        "builtin.module(func.func(buffer-deallocation-simplification, convert-linalg-to-loops), \
            eliminate-empty-tensors, func.func(llvm-request-c-wrappers), \
            convert-math-to-llvm, convert-math-to-libm, convert-scf-to-cf, \
            convert-arith-to-llvm, expand-strided-metadata, finalize-memref-to-llvm, \
            convert-func-to-llvm, reconcile-unrealized-casts)" \
      | sed 's|i64|i32|g' \
      > forward-lowered.mlir

    echo "Lowering subgraph0.mlir"
    buddy-opt subgraph0.mlir -pass-pipeline \
        "builtin.module(func.func(tosa-to-linalg-named, tosa-to-arith, tosa-to-linalg, tosa-to-tensor))" \
      | buddy-opt \
          -convert-elementwise-to-linalg \
          -func-bufferize-dynamic-offset \
          -arith-bufferize \
          -func-bufferize \
          -tensor-bufferize \
          -linalg-bufferize \
          -finalizing-bufferize \
          -batchmatmul-optimize \
          -convert-linalg-to-loops \
          -lower-affine \
          -lower-vector-exp \
          -lower-rvv=rv32 \
          -convert-vector-to-llvm \
          -convert-scf-to-cf \
          -llvm-request-c-wrappers \
          -convert-math-to-llvm \
          -convert-math-to-libm \
          -convert-arith-to-llvm \
          -convert-func-to-llvm \
          -expand-strided-metadata \
          -finalize-memref-to-llvm \
          -reconcile-unrealized-casts \
      | sed 's|i64|i32|g' \
      > subgraph0-lowered.mlir

    echo "Compiling memrefCopy library"
    $CXX -nostdlib -c ${../lib/MemrefCopy.cc} -o memrefCopy.o

    buddy-codegen arg -i arg0.data -o arg0.inc -s 11699112
    buddy-codegen img -i ${./dog-224_224.png} -o generated-img.inc

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
