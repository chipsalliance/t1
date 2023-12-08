{ testcase-env }:
testcase-env.mkIntrinsicCase {
  caseName = "matmul";
  srcs = [
    ./matmul.c
    ../main.S
  ];
}
