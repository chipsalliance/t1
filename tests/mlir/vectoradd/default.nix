{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "vectoradd";
  src = ./vectoradd.mlir;
  linkSrcs = [
    ../main.S
  ];
}
