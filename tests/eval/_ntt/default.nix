{ linkerScript
, makeBuilder
, t1main
}:

let
  builder = makeBuilder { casePrefix = "eval"; };
  build_ntt = caseName /* must be consistent with attr name */ : main_src: kernel_src:
    builder {
      caseName = caseName;

      src = ./.;

      passthru.featuresRequired = { };

      buildPhase = ''
        runHook preBuild

        $CC -T${linkerScript} \
          ${main_src} ${kernel_src} \
          ${t1main} \
          -o $pname.elf

        runHook postBuild
      '';

      meta.description = "test case 'ntt'";
    };

in {
  ntt_128 = build_ntt "ntt_128" ./ntt.c ./ntt_128_main.c;
  ntt_256 = build_ntt "ntt_256" ./ntt.c ./ntt_256_main.c;
  ntt_512 = build_ntt "ntt_512" ./ntt.c ./ntt_512_main.c;
  ntt_1024 = build_ntt "ntt_1024" ./ntt.c ./ntt_1024_main.c;
  ntt_mem_1024 = build_ntt "ntt_mem_1024" ./ntt_mem.c ./ntt_1024_main.c;
}
