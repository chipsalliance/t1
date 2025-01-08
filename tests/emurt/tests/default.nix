{ linkerScript, t1main, makeBuilder, writeText }:
let
  builder = makeBuilder { casePrefix = "emurt-test"; };
in
{
  simple = builder {
    caseName = "simple";

    dontUnpack = true;
    csrc = writeText "simple-emurt-test.c" ''
      #include <emurt.h>

      void test() {
        place_counter(1);
        print_s("Hello, World\n");
        place_counter(2);
      }
    '';

    buildPhase = ''
      runHook preBuild

      $CC -T${linkerScript} \
        $csrc \
        ${t1main} \
        -o $pname.elf

      runHook postBuild
    '';
  };
}
