{ _caseBuilders }:
_caseBuilders.mkMlirCase {
  caseName = "axpy_masked";
  src = ./axpy_masked.mlir;
  linkSrcs = [
    ../../t1_main.S
  ];
}
