{ testcase-env }:
testcase-env.mkAsmCase {
  caseName = "mmm_mem";
  srcs = [
    ./mmm.S
    ./mmm.c
    ../main.S
  ];
}
