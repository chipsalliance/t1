{ _caseBuilders }:
_caseBuilders.mkMlirCase {
  caseName = "hello";
  src = ./hello.mlir;
  linkSrcs = [
    ../../t1_main.S
    ./hello.c
  ];
}
