{ linkerScript
, makeBuilder
, python3
, t1main
}:

let
  builder = makeBuilder { casePrefix = "eval"; };
  seed = 0;
  pythonEnv = python3.withPackages (ps: with ps; [ numpy loguru ]);
  build_mmm = caseName /* must be consistent with attr name */: n:
    builder {
      caseName = caseName;

      src = ./.;

      passthru.featuresRequired = { };

      buildPhase = ''
        runHook preBuild

        ${pythonEnv}/bin/python3 ref.py -n ${toString n} --seed ${toString seed} \
          --gen-main > mmm_main.c

        $CC -T${linkerScript} \
          -I. \
          mmm_main.c \
          ${t1main} \
          -o $pname.elf

        runHook postBuild
      '';

      meta.description = "test case 'mmm'";
    };

in
{
  mmm_1024 = build_mmm "mmm_1024" 1024;
  mmm_2048 = build_mmm "mmm_2048" 2048;
  mmm_4096 = build_mmm "mmm_4096" 4096;
  mmm_8192 = build_mmm "mmm_8192" 8192;
}
