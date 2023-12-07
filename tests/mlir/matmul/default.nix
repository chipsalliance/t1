{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "matmul";
  src = ./matmul.mlir;
  linkSrcs = [
    ../main.S
  ];
}
