{ _caseBuilders }:
_caseBuilders.mkMlirCase {
  caseName = "conv";
  src = ./conv.mlir;
  linkSrcs = [
    ../../t1_main.S
  ];
}
