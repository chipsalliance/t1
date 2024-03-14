{ _caseBuilders }:
_caseBuilders.mkAsmCase {
  caseName = "strlen";
  srcs = [
    ./strlen.asm
    ../../t1_main.S
  ];
}
