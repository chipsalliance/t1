{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "maxvl_tail_setvl_front";
  src = ./maxvl-tail-setvl-front.mlir;
  linkSrcs = [
    ../main.S
  ];
}
