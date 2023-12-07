{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "hello";
  src = ./hello.mlir;
  linkSrcs = [
    ../main.S
  ];
}
