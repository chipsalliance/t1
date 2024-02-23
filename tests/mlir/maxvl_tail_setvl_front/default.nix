{ testcase-env }:
testcase-env.mkMlirCase {
  caseName = "maxvl_tail_setvl_front";
  src = ./maxvl_tail_setvl_front.mlir;
  linkSrcs = [
    ../main.S
  ];
}
