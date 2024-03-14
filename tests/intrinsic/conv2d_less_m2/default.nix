{ _caseBuilders }:
_caseBuilders.mkIntrinsicCase {
  caseName = "conv2d_less_m2";
  srcs = [
    ./conv2d_less_m2.c
    ../../t1_main.S
  ];
}
