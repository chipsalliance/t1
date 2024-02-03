{ testcase-env }:
testcase-env.mkIntrinsicCase {
  caseName = "uarttest";

  srcs = [
    ./uarttest.c
    ../main.S
  ];

  postUnpack = ''
    mkdir -p inc
    cp ${./uart.h} ./inc/uart.h
  '';

  preBuild = ''
    NIX_CFLAGS_COMPILE="-Iinc $NIX_CFLAGS_COMPILE"
  '';
}

