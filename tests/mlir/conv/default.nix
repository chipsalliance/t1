{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "conv";
  src = ./conv.mlir;
  linkSrcs = [
    ../main.S
  ];
}
