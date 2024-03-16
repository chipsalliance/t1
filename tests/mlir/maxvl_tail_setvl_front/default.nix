{ _caseBuilders }:
_caseBuilders.mkMlirCase {
  caseName = "maxvl_tail_setvl_front";
  src = ./maxvl_tail_setvl_front.mlir;
  linkSrcs = [
    ../../t1_main.S
    ./maxvl_tail_setvl_front.c
  ];
}
