{ testcase-env }:
testcase-env.mkIntrinsicCase {
  caseName = "conv2d_less_m2";
  srcs = [
    ./conv2d_less_m2.c
    ../main.S
  ];
}
