{ _caseBuilders }:
_caseBuilders.mkMlirCase {
  caseName = "matmul";
  src = ./matmul.mlir;
  linkSrcs = [
    ../../t1_main.S
  ];
}
