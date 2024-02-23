{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "rvv_vp_intrinsic_add";
  src = ./rvv_vp_intrinsic_add.mlir;
  linkSrcs = [
    ../main.S
  ];
}
