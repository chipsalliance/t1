{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "rvv_vp_intrinsic_add";
  src = ./rvv-vp-intrinsic-add.mlir;
  linkSrcs = [
    ../main.S
  ];
}
