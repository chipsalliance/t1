{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "rvv-vp-intrinsic-add";
  src = ./rvv-vp-intrinsic-add.mlir;
  linkSrcs = [
    ../main.S
  ];
}
