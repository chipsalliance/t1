{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "maxvl-tail-setvl-front";
  src = ./maxvl-tail-setvl-front.mlir;
  linkSrcs = [
    ../main.S
  ];
}
