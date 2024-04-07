{ _caseBuilders }:
_caseBuilders.mkIntrinsicCase {
  caseName = "linear_normalization";
  fp = true;
  srcs = [
    ./linear_normalization.c
    ../../t1_main.S
  ];
}
