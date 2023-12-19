{ testcase-env }:
testcase-env.mkIntrinsicCase {
  caseName = "linear_normalization";
  srcs = [
    ./linear_normalization.c
    ../main.S
  ];
}
