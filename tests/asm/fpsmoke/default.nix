{ _caseBuilders }:
_caseBuilders.mkAsmCase {
  caseName = "fpsmoke";
  fp = true;
  srcs = [
    ./fpsmoke.asm
    ../../t1_main.S
  ];
}
