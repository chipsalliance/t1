{ _caseBuilders }:
_caseBuilders.mkAsmCase {
  caseName = "memcpy";
  srcs = [
    ./memcpy.asm
    ../../t1_main.S
  ];
}
