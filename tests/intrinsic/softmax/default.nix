{ _caseBuilders }:
_caseBuilders.mkIntrinsicCase {
  caseName = "softmax";
  fp = true;
  srcs = [
    ./softmax.c
    ../../t1_main.S
  ];
}
