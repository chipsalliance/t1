{ testcase-env }:
testcase-env.mkAsmCase {
  caseName = "mmm_mem_scratchpad";
  srcs = [
    ./mmm.S
    ./mmm.c
    ../main.S
  ];
}
