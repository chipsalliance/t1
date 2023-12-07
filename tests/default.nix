{ callPackage }:

{
  axpy-masked-mlir = callPackage ./mlir/axpy-masked { };
  conv-mlir = callPackage ./mlir/conv { };
  hello-mlir = callPackage ./mlir/hello { };
  matmul-mlir = callPackage ./mlir/matmul { };
  maxvl-tail-setvl-front = callPackage ./mlir/maxvl-tail-setvl-front { };
  rvv-vp-intrinsic-add = callPackage ./mlir/rvv-vp-intrinsic-add { };
  rvv-vp-intrinsic-add-scalable = callPackage ./mlir/rvv-vp-intrinsic-add-scalable { };
  stripmining = callPackage ./mlir/stripmining { };
  vectoradd = callPackage ./mlir/vectoradd { };
}
