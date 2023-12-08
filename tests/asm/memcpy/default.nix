{ testcase-env }:
testcase-env.mkAsmCase {
  caseName = "memcpy";
  srcs = [
    ./memcpy.asm
    ../main.S
  ];
}
