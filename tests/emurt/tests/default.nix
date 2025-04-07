{
  linkerScript,
  t1main,
  makeBuilder,
  writeText,
}:
let
  builder = makeBuilder { casePrefix = "emurt-test"; };
in
{
  simple = builder {
    caseName = "simple";

    dontUnpack = true;
    csrc = writeText "simple-emurt-test.c" ''
      #include <emurt.h>
      #include <stdio.h>

      float array_b[100];

      void test() {
        place_counter(1);
        printf("Hello, %s", "World\n");
        for (int i = 1; i < 100; i++) {
          array_b[i] += 1 + array_b[i - 1];
        }
        place_counter(0);
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
