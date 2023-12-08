{ testcase-env }:
testcase-env.mkAsmCase {
  caseName = "smoke";
  srcs = [
    ./smoke.asm
    ../main.S
  ];
}
