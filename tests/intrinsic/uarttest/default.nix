{ testcase-env }:
testcase-env.mkIntrinsicCase {
  caseName = "uarttest";
  srcs = [
    ./uarttest.c
    ./uart.h
    ../main.S
  ];
}