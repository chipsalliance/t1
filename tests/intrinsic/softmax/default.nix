{ testcase-env }:
testcase-env.mkIntrinsicCase {
  caseName = "softmax";
  srcs = [
    ./softmax.c
    ../main.S
  ];
}
