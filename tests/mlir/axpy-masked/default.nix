{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "axpy-masked";
  src = ./axpy-masked.mlir;
  linkSrcs = [
    ../main.S
  ];
}
