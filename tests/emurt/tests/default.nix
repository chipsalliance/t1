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
    src = writeText "simple-emurt-test.c" ''
      #include <emurt.h>
      #include <stdio.h>
      #include <stdlib.h>

      float array_b[100];

      void test() {
        place_counter(1);
        printf("Hello, %s", "World\n");
        for (int i = 1; i < 100; i++) {
          array_b[i] += 1 + array_b[i - 1];
        }
        place_counter(0);

        int pool_size = 32;
        int* pool = (int*)dram_alloc(pool_size * sizeof(int));
        for (int i = 0; i < pool_size; i++) {
          pool[i] = array_b[i];
        }

        int slice_size = 8;
        int* slice = (int*)malloc(slice_size * sizeof(int));
        for (int i = 0; i < slice_size; i++) {
          slice[i] = pool[i];
        }

        free(slice);
      }
    '';

    buildPhase = ''
      runHook preBuild

      $CC -T${linkerScript} \
        $src \
        ${t1main} \
        -o $pname.elf

      runHook postBuild
    '';
  };
}
