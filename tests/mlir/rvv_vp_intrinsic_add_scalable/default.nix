{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "rvv_vp_intrinsic_add_scalable";
  src = ./rvv_vp_intrinsic_add_scalable.mlir;
  linkSrcs = [
    ../main.S
  ];
}
