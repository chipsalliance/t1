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
