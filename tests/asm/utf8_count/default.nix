{ _caseBuilders }:
_caseBuilders.mkAsmCase {
  caseName = "utf8_count";
  srcs = [
    ./utf8-count.asm
    ../../t1_main.S
  ];
}
