{
  includes = [
    ../memref.hpp
  ];

  buddyOptArgs = [
    [
      "--pass-pipeline"
      "builtin.module(func.func(tosa-to-linalg-named, tosa-to-arith, tosa-to-linalg, tosa-to-tensor))"
    ]
    [
      "--convert-elementwise-to-linalg"
      "--func-bufferize-dynamic-offset"
      "--arith-bufferize"
      "--func-bufferize"
      "--tensor-bufferize"
      "--linalg-bufferize"
      "--finalizing-bufferize"
      "--batchmatmul-optimize"
      "--convert-linalg-to-affine-loops"
      "--lower-affine"
      "--lower-vector-exp"
      "--lower-rvv=rv32"
      "--convert-vector-to-scf"
      "--convert-scf-to-cf"
      "--llvm-request-c-wrappers"
      "--convert-vector-to-llvm"
      "--convert-math-to-llvm"
      "--convert-math-to-libm"
      "--convert-arith-to-llvm"
      "--convert-func-to-llvm"
      "--expand-strided-metadata"
      "--finalize-memref-to-llvm"
      "--reconcile-unrealized-casts"
    ]
  ];
}
