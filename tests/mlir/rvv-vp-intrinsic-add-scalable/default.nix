{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "rvv-vp-intrinsic-add-scalable";
  src = ./rvv-vp-intrinsic-add-scalable.mlir;
  linkSrcs = [
    ../main.S
  ];
}
