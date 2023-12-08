{ testcase-env }:
testcase-env.mkAsmCase {
  caseName = "strlen";
  srcs = [
    ./strlen.asm
    ../main.S
  ];
}
