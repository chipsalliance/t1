{
  includes = [
    ../memref.hpp
  ];

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
      "--arith-expand"
      "--eliminate-empty-tensors"
      "--empty-tensor-to-alloc-tensor"
      "--one-shot-bufferize"
      "--matmul-paralell-vectorization-optimize"
      "--batchmatmul-optimize"
      "--convert-linalg-to-affine-loops"
      "--affine-loop-fusion"
      "--affine-parallelize"
      "--lower-affine"
      "--convert-scf-to-openmp"
      "--func-bufferize-dynamic-offset"
      "--tensor-bufferize"
      "--arith-bufferize"
      "--buffer-deallocation"
      "--finalizing-bufferize"
      "--convert-vector-to-scf"
      "--expand-strided-metadata"
      "--cse"
      "--lower-vector-exp"
      "--lower-rvv=rv32"
      "--convert-vector-to-llvm"
      "--memref-expand"
      "--arith-expand"
      "--convert-arith-to-llvm"
      "--finalize-memref-to-llvm"
      "--convert-scf-to-cf"
      "--llvm-request-c-wrappers"
      "--convert-openmp-to-llvm"
      "--convert-arith-to-llvm"
      "--convert-math-to-llvm"
      "--convert-math-to-libm"
      "--convert-func-to-llvm"
      "--reconcile-unrealized-casts"
    ]
  ];
}
