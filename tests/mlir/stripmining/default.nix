{ _caseBuilders }:
_caseBuilders.mkMlirCase {
  caseName = "stripmining";
  src = ./stripmining.mlir;
  linkSrcs = [
    ../../t1_main.S
  ];
}
