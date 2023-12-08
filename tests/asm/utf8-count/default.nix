{ testcase-env }:
testcase-env.mkAsmCase {
  caseName = "utf8-count";
  srcs = [
    ./utf8-count.asm
    ../main.S
  ];
}
