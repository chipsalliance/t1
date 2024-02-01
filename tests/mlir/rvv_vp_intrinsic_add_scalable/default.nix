{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "rvv_vp_intrinsic_add_scalable";
  src = ./rvv-vp-intrinsic-add-scalable.mlir;
  linkSrcs = [
    ../main.S
  ];
}
