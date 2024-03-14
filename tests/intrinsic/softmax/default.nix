{ _caseBuilders }:
_caseBuilders.mkIntrinsicCase {
  caseName = "softmax";
  srcs = [
    ./softmax.c
    ../../t1_main.S
  ];
}
