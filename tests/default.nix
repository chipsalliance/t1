{ callPackage }:

{
  mlir = {
    axpy-masked = callPackage ./mlir/axpy-masked { };
    conv = callPackage ./mlir/conv { };
    hello = callPackage ./mlir/hello { };
    matmul = callPackage ./mlir/matmul { };
    maxvl-tail-setvl-front = callPackage ./mlir/maxvl-tail-setvl-front { };
    rvv-vp-intrinsic-add = callPackage ./mlir/rvv-vp-intrinsic-add { };
    rvv-vp-intrinsic-add-scalable = callPackage ./mlir/rvv-vp-intrinsic-add-scalable { };
    stripmining = callPackage ./mlir/stripmining { };
    vectoradd = callPackage ./mlir/vectoradd { };
  };

  intrinsic = {
    matmul = callPackage ./intrinsic/matmul { };
    conv2d-less-m2 = callPackage ./intrinsic/conv2d_less_m2 { };
  };

  asm = {
    fpsmoke = callPackage ./asm/fpsmoke { };
    memcpy = callPackage ./asm/memcpy { };
    mmm = callPackage ./asm/mmm { };
    smoke = callPackage ./asm/smoke { };
    strlen = callPackage ./asm/strlen { };
    utf8-count = callPackage ./asm/utf8-count { };
  };
}
