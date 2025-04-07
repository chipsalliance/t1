{ buildBuddyE2ETest, fetchurl }:
let
  lenetModel = fetchurl {
    url = "https://raw.githubusercontent.com/buddy-compiler/buddy-benchmark/1e166d53faae6d96a209645688cd9ab1d6eb604d/benchmarks/DeepLearning/Models/LeNet/lenet_model.pth";
    hash = "sha256-OqUzJ9vF1GF6jMVlSm0AYowLk4ypiR/Qs2KD9NMQJfg=";
  };
in
buildBuddyE2ETest {
  caseName = "lenet";

  optPhase = ''
    export LENET_MODEL_PATH=${lenetModel}
    python ./lenet.py

    echo "Lowering forward.mlir"
    buddy-opt forward.mlir -pass-pipeline \
        "builtin.module(func.func(tosa-to-linalg-named, tosa-to-linalg, tosa-to-tensor, tosa-to-arith{use-32-bit}), \
                                  empty-tensor-to-alloc-tensor, convert-elementwise-to-linalg, arith-bufferize, \
                                  func.func(linalg-bufferize, tensor-bufferize), func-bufferize)" \
      | buddy-opt -pass-pipeline \
        "builtin.module(func.func(buffer-deallocation-simplification, convert-linalg-to-loops), \
                                  eliminate-empty-tensors, func.func(llvm-request-c-wrappers), \
                                  convert-math-to-llvm, convert-scf-to-cf, \
                                  convert-arith-to-llvm{index-bitwidth=32}, expand-strided-metadata, finalize-memref-to-llvm{index-bitwidth=32}, \
                                  convert-func-to-llvm{index-bitwidth=32}, reconcile-unrealized-casts)" \
      > forward-lowered.mlir

    echo "Lowering subgraphs[0]"
    buddy-opt subgraphs0.mlir -pass-pipeline \
        "builtin.module(func.func(tosa-to-linalg-named, tosa-to-arith{use-32-bit}, tosa-to-linalg, tosa-to-tensor))" \
      | buddy-opt \
          --eliminate-empty-tensors \
          --convert-tensor-to-linalg \
          --linalg-bufferize \
          --convert-linalg-to-loops \
          --lower-affine \
          --func-bufferize-dynamic-offset \
          --arith-bufferize \
          --tensor-bufferize \
          --buffer-deallocation \
          --finalizing-bufferize \
          --memref-expand \
          --llvm-request-c-wrappers \
          --convert-vector-to-llvm=force-32bit-vector-indices \
          --lower-vector-exp \
          --lower-rvv=rv32 \
          --expand-strided-metadata \
          --finalize-memref-to-llvm=index-bitwidth=32 \
          --convert-index-to-llvm=index-bitwidth=32 \
          --convert-scf-to-cf \
          --convert-arith-to-llvm=index-bitwidth=32 \
          --convert-func-to-llvm=index-bitwidth=32 \
          --reconcile-unrealized-casts \
      > subgraphs0-lowered.mlir

    optArtifacts+=(
      "forward-lowered.mlir"
      "subgraphs0-lowered.mlir"
    )

    mkdir -p "$out/resources"
    cp -v ''${optArtifacts[*]} "$out/resources"
  '';
}
