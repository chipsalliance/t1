{ testcase-env }:
testcase-env.mkAsmCase {
  caseName = "fpsmoke";
  srcs = [
    ./fpsmoke.asm
    ../main.S
  ];
}
