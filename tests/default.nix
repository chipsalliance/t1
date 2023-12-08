{ callPackage }:

{
  axpy-masked-mlir = callPackage ./mlir/axpy-masked { };
  conv-mlir = callPackage ./mlir/conv { };
  hello-mlir = callPackage ./mlir/hello { };
  matmul-mlir = callPackage ./mlir/matmul { };
  maxvl-tail-setvl-front-mlir = callPackage ./mlir/maxvl-tail-setvl-front { };
  rvv-vp-intrinsic-add-mlir = callPackage ./mlir/rvv-vp-intrinsic-add { };
  rvv-vp-intrinsic-add-scalable-mlir = callPackage ./mlir/rvv-vp-intrinsic-add-scalable { };
  stripmining-mlir = callPackage ./mlir/stripmining { };
  vectoradd-mlir = callPackage ./mlir/vectoradd { };

  matmul-intrinsic = callPackage ./intrinsic/matmul { };
  conv2d-less-m2-intrinsic = callPackage ./intrinsic/conv2d_less_m2 { };
}
