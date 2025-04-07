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
    python3 ./tinyllama.py --output-dir $PWD

    echo "Lowering forward.mlir"
      cat forward.mlir \
      | buddy-opt \
          --expand-strided-metadata \
          --finalize-memref-to-llvm=index-bitwidth=32 \
          --llvm-request-c-wrappers \
          --convert-func-to-llvm=index-bitwidth=32 \
          --reconcile-unrealized-casts \
      > forward-lowered.mlir

    echo "Lowering subgraphs[0]"
    buddy-opt subgraph0.mlir -pass-pipeline \
        "builtin.module(func.func(tosa-to-linalg-named, tosa-to-arith{use-32-bit}, tosa-to-linalg, tosa-to-tensor))" \
      | buddy-opt \
          --convert-elementwise-to-linalg \
          --arith-expand \
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
          --convert-vector-to-scf \
          --expand-strided-metadata \
          --llvm-request-c-wrappers \
          --cse \
          --lower-vector-exp \
          --lower-rvv=rv32 \
          --convert-vector-to-llvm \
          --memref-expand \
          --arith-expand \
          --convert-arith-to-llvm=index-bitwidth=32 \
          --finalize-memref-to-llvm=index-bitwidth=32 \
          --convert-scf-to-cf \
          --convert-arith-to-llvm=index-bitwidth=32 \
          --convert-math-to-llvm \
          --convert-func-to-llvm=index-bitwidth=32 \
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

    mkdir -p "$out/resources"
    cp -v ''${optArtifacts[*]} "$out/resources"
  '';
}
