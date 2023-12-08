{ testcase-env }:
testcase-env.mkAsmCase {
  caseName = "mmm";
  srcs = [
    ./mmm.asm
    ../main.S
  ];
}
