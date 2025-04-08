{ buildBuddyE2ETest }:
buildBuddyE2ETest {
  caseName = "matmul";

  optPhase = ''
    echo "Lowering forward.mlir"

    python ./matmul.py \
      | buddy-opt --pass-pipeline "builtin.module(func.func(tosa-to-linalg-named, tosa-to-arith{use-32-bit}, tosa-to-linalg, tosa-to-tensor))" \
    | buddy-opt \
        --convert-elementwise-to-linalg \
        --one-shot-bufferize="bufferize-function-boundaries" \
        --func-bufferize-dynamic-offset \
        --convert-linalg-to-affine-loops \
        --batchmatmul-optimize \
        --lower-affine \
        --lower-vector-exp \
        --lower-rvv=rv32 \
        --convert-vector-to-scf \
        --convert-scf-to-cf \
        --convert-cf-to-llvm \
        --llvm-request-c-wrappers \
        --convert-vector-to-llvm \
        --convert-math-to-llvm \
        --convert-arith-to-llvm=index-bitwidth=32 \
        --convert-func-to-llvm=index-bitwidth=32 \
        --expand-strided-metadata \
        --finalize-memref-to-llvm=index-bitwidth=32 \
        --reconcile-unrealized-casts \
        -o forward-lowered.mlir

    optArtifacts+=(
      "forward-lowered.mlir"
    )
  '';
}
