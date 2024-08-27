{ buildBuddyE2ETest, fetchgit }:
let
  model = fetchgit {
    url = "https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0";
    rev = "fe8a4ea1ffedaf415f4da2f062534de366a451e6";
    fetchLFS = true;
    hash = "sha256-vp/aUHKX+NJZZMIk2CgSh2czeGD0HeQGS30p/If2pA0=";
  };
in
buildBuddyE2ETest {
  caseName = "tinyllama";

  passthru.model = model;

  env.LLAMA_MODEL_PATH = "${model}";
  optPhase = ''
    python ./tinyllama.py

    echo "Lowering forward.mlir"
    buddy-opt forward.mlir -pass-pipeline \
      "builtin.module(func.func(tosa-to-linalg-named),func.func(tosa-to-linalg),\
      func.func(tosa-to-tensor),func.func(tosa-to-arith))" \
      | buddy-opt --arith-expand \
        --eliminate-empty-tensors \
        --empty-tensor-to-alloc-tensor \
        --one-shot-bufferize \
        --batchmatmul-optimize \
        --convert-linalg-to-affine-loops \
        --affine-loop-fusion \
        --lower-affine \
        --func-bufferize \
        --arith-bufferize \
        --tensor-bufferize \
        --buffer-deallocation \
        --finalizing-bufferize \
        --convert-vector-to-scf \
        --expand-strided-metadata \
        --convert-vector-to-llvm \
        --memref-expand \
        --arith-expand \
        --convert-arith-to-llvm \
        --finalize-memref-to-llvm \
        --convert-scf-to-cf \
        --llvm-request-c-wrappers \
        --convert-openmp-to-llvm \
        --convert-arith-to-llvm \
        --convert-math-to-llvm \
        --convert-math-to-libm  \
        --convert-func-to-llvm \
        --reconcile-unrealized-casts \
      > forward-lowered.mlir

    echo "Lowering subgraphs[0]"
    buddy-opt subgraphs0.mlir -pass-pipeline \
        "builtin.module(func.func(tosa-to-linalg-named, tosa-to-arith, tosa-to-linalg, tosa-to-tensor))" \
      | buddy-opt \
          --arith-expand \
          --eliminate-empty-tensors \
          --empty-tensor-to-alloc-tensor \
          --one-shot-bufferize \
          --batchmatmul-optimize \
          --convert-linalg-to-affine-loops \
          --affine-loop-fusion \
          --lower-affine \
          --func-bufferize-dynamic-offset \
          --tensor-bufferize \
          --arith-bufferize \
          --buffer-deallocation \
          --finalizing-bufferize \
          --convert-vector-to-scf \
          --expand-strided-metadata \
          --cse \
          --lower-vector-exp \
          --lower-rvv=rv32 \
          --convert-vector-to-llvm \
          --memref-expand \
          --arith-expand \
          --convert-arith-to-llvm \
          --finalize-memref-to-llvm \
          --convert-scf-to-cf \
          --llvm-request-c-wrappers \
          --convert-openmp-to-llvm \
          --convert-arith-to-llvm \
          --convert-math-to-llvm \
          --convert-math-to-libm  \
          --convert-func-to-llvm \
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
