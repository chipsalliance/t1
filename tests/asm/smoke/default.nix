{ _caseBuilders }:
_caseBuilders.mkAsmCase {
  caseName = "smoke";
  srcs = [
    ./smoke.asm
    ../../t1_main.S
  ];
}
