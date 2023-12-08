{ testcase-env }:
testcase-env.mkAsmCase {
  caseName = "fpsmoke";
  fp = true;
  srcs = [
    ./fpsmoke.asm
    ../main.S
  ];
}
