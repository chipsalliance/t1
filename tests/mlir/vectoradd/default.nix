{ _caseBuilders }:
_caseBuilders.mkMlirCase {
  caseName = "vectoradd";
  src = ./vectoradd.mlir;
  linkSrcs = [
    ../../t1_main.S
    ./vectoradd.c
  ];
}
