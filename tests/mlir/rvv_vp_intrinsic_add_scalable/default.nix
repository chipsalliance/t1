{ _caseBuilders }:
_caseBuilders.mkMlirCase {
  caseName = "rvv_vp_intrinsic_add_scalable";
  src = ./rvv_vp_intrinsic_add_scalable.mlir;
  linkSrcs = [
    ../../t1_main.S
  ];
}
