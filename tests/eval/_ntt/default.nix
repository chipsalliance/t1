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
  ntt_64 = build_ntt "ntt_64" ./ntt.c ./ntt_64_main.c;
  ntt_128 = build_ntt "ntt_128" ./ntt.c ./ntt_128_main.c;
  ntt_256 = build_ntt "ntt_256" ./ntt.c ./ntt_256_main.c;
  ntt_512 = build_ntt "ntt_512" ./ntt.c ./ntt_512_main.c;
  ntt_1024 = build_ntt "ntt_1024" ./ntt.c ./ntt_1024_main.c;
  ntt_4096 = build_ntt "ntt_4096" ./ntt.c ./ntt_4096_main.c;

  ntt_mem_64 = build_ntt "ntt_mem_64" ./ntt_mem.c ./ntt_64_main.c;
  ntt_mem_128 = build_ntt "ntt_mem_128" ./ntt_mem.c ./ntt_128_main.c;
  ntt_mem_256 = build_ntt "ntt_mem_256" ./ntt_mem.c ./ntt_256_main.c;
  ntt_mem_512 = build_ntt "ntt_mem_512" ./ntt_mem.c ./ntt_512_main.c;
  ntt_mem_1024 = build_ntt "ntt_mem_1024" ./ntt_mem.c ./ntt_1024_main.c;
  ntt_mem_4096 = build_ntt "ntt_mem_4096" ./ntt_mem.c ./ntt_4096_main.c;
}
