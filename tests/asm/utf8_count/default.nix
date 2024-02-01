{ testcase-env }:
testcase-env.mkAsmCase {
  caseName = "utf8_count";
  srcs = [
    ./utf8-count.asm
    ../main.S
  ];
}
