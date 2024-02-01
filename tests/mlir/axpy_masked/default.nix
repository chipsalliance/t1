{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "axpy_masked";
  src = ./axpy-masked.mlir;
  linkSrcs = [
    ../main.S
  ];
}
