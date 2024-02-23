{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "axpy_masked";
  src = ./axpy_masked.mlir;
  linkSrcs = [
    ../main.S
  ];
}
