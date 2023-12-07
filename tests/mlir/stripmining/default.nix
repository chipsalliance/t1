{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "stripmining";
  src = ./stripmining.mlir;
  linkSrcs = [
    ../main.S
  ];
}
