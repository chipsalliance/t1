{ _caseBuilders }:
_caseBuilders.mkIntrinsicCase {
  caseName = "matmul";
  srcs = [
    ./matmul.c
    ../../t1_main.S
  ];
}
