{ _caseBuilders }:
_caseBuilders.mkMlirCase {
  caseName = "rvv_vp_intrinsic_add";
  src = ./rvv_vp_intrinsic_add.mlir;
  linkSrcs = [
    ../../t1_main.S
  ];
}
